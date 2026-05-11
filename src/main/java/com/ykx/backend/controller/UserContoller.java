package com.ykx.backend.controller;

import cn.hutool.core.util.StrUtil;
import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.dto.SsoExchangeDTO;
import com.ykx.backend.model.dto.UsersLoginDTO;
import com.ykx.backend.model.dto.UsersRegisterDTO;
import com.ykx.backend.model.dto.UsersUpdateDTO;
import com.ykx.backend.model.vo.LoginData;
import com.ykx.backend.model.vo.user.SsoCodeVO;
import com.ykx.backend.model.vo.user.UsersInfoVO;
import com.ykx.backend.model.vo.user.UsersLoginVO;
import com.ykx.backend.model.vo.user.UsersRegisterVO;
import com.ykx.backend.model.vo.user.UsersUpdateVO;
import com.ykx.backend.common.ClientIpUtils;
import com.ykx.backend.service.UsersService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.jdbc.Null;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/user")
public class UserContoller {
    @Resource
    private UsersService usersService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public BaseResponse<LoginData<UsersLoginVO>> login(HttpServletRequest request, @RequestBody UsersLoginDTO loginDTO) {
        if (loginDTO == null || StrUtil.isBlank(loginDTO.getUsername()) || StrUtil.isBlank(loginDTO.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        return usersService.login(loginDTO, ClientIpUtils.resolve(request));
    }
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public BaseResponse<UsersRegisterVO> register(HttpServletRequest request, @RequestBody UsersRegisterDTO registerDTO) {
        if (registerDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return usersService.register(registerDTO, ClientIpUtils.resolve(request));
    }

    /**
     * 刷新 access_token
     */
    @PostMapping("/refresh")
    public BaseResponse<LoginData<Null>> refreshToken(@RequestParam("refresh_token") String refreshToken) {
        if (StrUtil.isBlank(refreshToken)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return usersService.refreshToken(refreshToken);
    }

    /**
     * 站点 A 已登录后调用，生成一次性 SSO 票据（需在 Authorization 中带 Bearer access_token）
     */
    @PostMapping("/sso/code")
    public BaseResponse<SsoCodeVO> createSsoCode() {
        return usersService.createSsoBridgeCode();
    }

    /**
     * 站点 B 携带票据换取与本站登录相同结构的 token（无需 Authorization）
     */
    @PostMapping("/sso/exchange")
    public BaseResponse<LoginData<UsersLoginVO>> exchangeSso(@RequestBody SsoExchangeDTO dto) {
        if (dto == null || StrUtil.isBlank(dto.getCode())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "code 不能为空");
        }
        return usersService.exchangeSsoCode(dto.getCode());
    }

    // ======================== 用户信息 ========================

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    public BaseResponse<UsersInfoVO> getUserInfo() {
        return usersService.getUserInfo();
    }

    /**
     * 更新用户资料（JSON，不含文件头像）
     */
    @PostMapping(value = "/update", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<UsersUpdateVO> updateUserJson(@RequestBody UsersUpdateDTO updateDTO) {
        if (updateDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return usersService.update(updateDTO, null);
    }

    /**
     * 更新用户资料（multipart）：表单字段与 {@link UsersUpdateDTO} 同名即可绑定；
     * 本地头像文件字段名 {@code file} 或 {@code avatarFile}（二选一，可选）。
     */
    @PostMapping(value = "/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<UsersUpdateVO> updateUserMultipart(
            @ModelAttribute UsersUpdateDTO updateDTO,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile
    ) {
        UsersUpdateDTO dto = updateDTO != null ? updateDTO : new UsersUpdateDTO();
        MultipartFile pic = file != null && !file.isEmpty() ? file : avatarFile;
        return usersService.update(dto, pic);
    }

    /**
     * 重置密码（修改密码）
     */
    @PostMapping("/reset-pwd")
    public BaseResponse<Null> resetPassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            @RequestParam String confirm_password
    ) {
        if (StrUtil.isBlank(oldPassword) || StrUtil.isBlank(newPassword)||StrUtil.isBlank(confirm_password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return usersService.resetPassword(oldPassword, newPassword, confirm_password);
    }

    /**
     * 注销登录
     */
    @PostMapping("/logout")
    public BaseResponse<Null> logout() {
        return usersService.logout();
    }
}
