package com.ykx.backend.controller;

import cn.hutool.core.util.StrUtil;
import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.dto.UsersLoginDTO;
import com.ykx.backend.model.dto.UsersRegisterDTO;
import com.ykx.backend.model.dto.UsersUpdateDTO;
import com.ykx.backend.model.vo.LoginData;
import com.ykx.backend.model.vo.user.UsersInfoVO;
import com.ykx.backend.model.vo.user.UsersLoginVO;
import com.ykx.backend.model.vo.user.UsersRegisterVO;
import com.ykx.backend.model.vo.user.UsersUpdateVO;
import com.ykx.backend.service.UsersService;
import jakarta.annotation.Resource;
import org.apache.ibatis.jdbc.Null;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserContoller {
    @Resource
    private UsersService usersService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public BaseResponse<LoginData<UsersLoginVO>> login(@RequestBody UsersLoginDTO loginDTO) {
        if (loginDTO == null || StrUtil.isBlank(loginDTO.getUsername()) || StrUtil.isBlank(loginDTO.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        return usersService.login(loginDTO);
    }
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public BaseResponse<UsersRegisterVO> register(@RequestBody UsersRegisterDTO registerDTO) {
        if (registerDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return usersService.register(registerDTO);
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

    // ======================== 用户信息 ========================

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    public BaseResponse<UsersInfoVO> getUserInfo() {
        return usersService.getUserInfo();
    }

    /**
     * 更新用户资料
     */
    @PostMapping("/update")
    public BaseResponse<UsersUpdateVO> updateUser(
            @RequestBody UsersUpdateDTO updateDTO
    ) {
        if (updateDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return usersService.update(updateDTO);
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
