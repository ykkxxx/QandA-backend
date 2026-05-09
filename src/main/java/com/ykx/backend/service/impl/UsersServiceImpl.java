package com.ykx.backend.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.dto.UsersLoginDTO;
import com.ykx.backend.model.dto.UsersRegisterDTO;
import com.ykx.backend.model.dto.UsersUpdateDTO;
import com.ykx.backend.model.entity.Users;
import com.ykx.backend.model.vo.LoginData;
import com.ykx.backend.model.vo.user.UsersInfoVO;
import com.ykx.backend.model.vo.user.UsersLoginVO;
import com.ykx.backend.model.vo.user.UsersRegisterVO;
import com.ykx.backend.model.vo.user.UsersUpdateVO;
import com.ykx.backend.service.UsersService;
import com.ykx.backend.mapper.UsersMapper;
import org.apache.ibatis.jdbc.Null;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
* @author 13797
* @description 针对表【users(用户表)】的数据库操作Service实现
* @createDate 2026-05-08 10:05:28
*/
@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users>
    implements UsersService{
    // 密码加密盐
    private static final String SALT = "Ykx_2025_Rag_System_Salt";

    // JWT 秘钥
    private static final String JWT_SECRET = "Ykx_JWT_Secret_2025_123456789";

    // Access Token 有效期：2小时
    private static final long ACCESS_EXPIRE = 7200L;
    // Refresh Token 有效期：7天
    private static final long REFRESH_EXPIRE = 604800L;
    @Override
    public BaseResponse<LoginData<UsersLoginVO>> login(UsersLoginDTO usersLoginDTO) {
        // 1. 获取用户名和密码
        String username = usersLoginDTO.getUsername();
        String password = usersLoginDTO.getPassword();

        // 2. 校验用户名格式 3-20位 只能包含字母、数字、下划线
        if (!username.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户名格式错误：3-20位，仅允许字母、数字、下划线");
        }

        // 3. 校验密码格式 6-32位 只能包含字母、数字、下划线
        if (!password.matches("^[a-zA-Z0-9_]{6,32}$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"密码格式错误：6-32位，仅允许字母、数字、下划线");
        }

        // 4. 校验用户名和密码是否匹配
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Users::getUsername, username);
        Users user = getOne(wrapper);
        System.out.println("密码：" + user.getPassword_hash());
        // 账号不存在
        if (user == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR,"用户名或密码错误");
        }

        // 密码比对
        String encryptPassword = SecureUtil.sha256(password + SALT);
        System.out.println("注册-加密结果：" + encryptPassword);
        System.out.println("注册-加密结果：" + user.getPassword_hash());
        if (!encryptPassword.equals(user.getPassword_hash())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名或密码错误");
        }

        // 5.生成jwt
        Map<String, Object> commonPayload = new HashMap<>();
        commonPayload.put("userId", user.getUuid());
        commonPayload.put("username", user.getUsername());
        // 1. 生成 access_token（2小时）
        Map<String, Object> accessPayload = new HashMap<>(commonPayload);
        accessPayload.put("exp", System.currentTimeMillis() / 1000 + ACCESS_EXPIRE);
        String access_token = JWTUtil.createToken(accessPayload, JWT_SECRET.getBytes());

        // 2. 生成 refresh_token（7天）
        Map<String, Object> refreshPayload = new HashMap<>(commonPayload);
        refreshPayload.put("exp", System.currentTimeMillis() / 1000 + REFRESH_EXPIRE);
        String refresh_token = JWTUtil.createToken(refreshPayload, JWT_SECRET.getBytes());

        //更新登录时间
        user.setLast_login(new Date());
        this.updateById(user);
        // 6.封装返回
        UsersLoginVO usersVO = BeanUtil.copyProperties(user, UsersLoginVO.class);
        LoginData<UsersLoginVO> loginData = new LoginData<>();
        loginData.setAccess_token(access_token);
        loginData.setRefresh_token(refresh_token);
        loginData.setExpires_in((int) ACCESS_EXPIRE);
        loginData.setToken_type("Bearer");
        loginData.setUser(usersVO);

        // 5.4 返回统一结果
        return ResultUtils.success(loginData);
    }

    @Override
    public BaseResponse<UsersRegisterVO> register(UsersRegisterDTO usersRegisterVODTO) {
        // 1. 获取用户名 密码 确认密码
        String username = usersRegisterVODTO.getUsername();
        String password = usersRegisterVODTO.getPassword();
        System.out.println("注册-原始密码：" + password);
        String confirmPassword = usersRegisterVODTO.getConfirm_password();
        // 2. 校验用户名、密码格式
        if (!username.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名格式错误：3-20位，字母/数字/下划线");
        }
        if (!password.matches("^[a-zA-Z0-9_]{6,32}$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码格式错误：6-32位");
        }
        // 3. 校验两次密码是否一致
        if (!password.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码输入不一致");
        }
        // 4. 校验用户是否已存在
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Users::getUsername, username);
        long count = this.count(wrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名已存在");
        }
        // 5. 密码加密（SHA256 + 盐）
        String encryptPassword = SecureUtil.sha256(password + SALT);
        System.out.println("注册-加密结果：" + encryptPassword);
        // 6. 构建用户实体（必须补全默认字段，否则入库报错）
        Users user = new Users();
        user.setUsername(username);
        user.setPassword_hash(encryptPassword); // 存密文
        user.setEmail(usersRegisterVODTO.getEmail());
        user.setTelephone(usersRegisterVODTO.getTelephone());
        user.setStatus(1);    // 正常状态
        user.setDate_joined(new Date());
        // 7. 入库
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，请稍后重试");
        }
        // 8. 封装返回VO（脱敏！）
        UsersRegisterVO registerVO = new UsersRegisterVO();
        BeanUtil.copyProperties(user,registerVO);
        return ResultUtils.success(registerVO);
    }

    @Override
    public BaseResponse<LoginData<Null>> refreshToken(String refresh_token) {
        // 1. 判断 refresh_token 是否为空
        if (StrUtil.isBlank(refresh_token)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "refresh_token 不能为空");
        }

        // 2. 校验 JWT 合法性 + 过期时间
        boolean isValid;
        try {
            isValid = JWTUtil.verify(refresh_token, JWT_SECRET.getBytes());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "refresh_token 非法");
        }

        if (!isValid) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "refresh_token 无效或已过期");
        }

        // 3. 解析用户信息
        String userId = (String) JWTUtil.parseToken(refresh_token).getPayload("userId");
        String username = (String) JWTUtil.parseToken(refresh_token).getPayload("username");

        // 4. 只生成 新 access_token（关键！）
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("exp", System.currentTimeMillis() / 1000 + ACCESS_EXPIRE);
        String newAccessToken = JWTUtil.createToken(payload, JWT_SECRET.getBytes());

        // 5. 封装返回
        LoginData<Null> loginData = new LoginData<>();
        loginData.setAccess_token(newAccessToken);
        loginData.setRefresh_token(refresh_token); // 保持原来的！
        loginData.setExpires_in((int) ACCESS_EXPIRE);
        loginData.setToken_type("Bearer");
        return ResultUtils.success(loginData);
    }

    @Override
    public BaseResponse<UsersInfoVO> getUserInfo() {
        // 1. 从 UserHolder 中解析 userId
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "token 解析失败");
        }

        // 3. 根据 id 查询数据库用户信息
        Users user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        // 4. 转成 VO 返回（脱敏，不返回密码）
        UsersInfoVO usersInfoVO = new UsersInfoVO();
        BeanUtil.copyProperties(user, usersInfoVO);
        return ResultUtils.success(usersInfoVO);
    }

    @Override
    public BaseResponse<UsersUpdateVO> update(UsersUpdateDTO updateDTO) {
        // 3. 从 token 拿 userId（核心！不能用前端传的 id）
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "token 无效");
        }

        // 4. 查询用户是否存在
        Users existUser = getById(userId);
        if (existUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        // 5. 安全更新：只赋值允许修改的字段，不整体拷贝
        existUser.setEmail(updateDTO.getEmail());
        existUser.setTelephone(updateDTO.getTelephone());
        // existUser.setNickname(updateDTO.getNickname()); 你需要啥加啥

        // 6. 更新数据库
        boolean updateSuccess = updateById(existUser);
        if (!updateSuccess) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败");
        }

        // 7. 转 VO 返回
        UsersUpdateVO updateVO = new UsersUpdateVO();
        BeanUtil.copyProperties(existUser, updateVO);

        return ResultUtils.success(updateVO);
    }

    @Override
    public BaseResponse<Null> resetPassword(String oldPassword, String newPassword, String confirm_password) {
        // 2. 从 token 获取当前登录用户 ID
        String userId = UserContext.getUserId();
        Users user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        // 3. 校验旧密码是否正确
        String oldEncrypt = SecureUtil.sha256(oldPassword + SALT);
        if (!oldEncrypt.equals(user.getPassword_hash())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "旧密码不正确");
        }

        // ===================== 【新增】确认密码校验 =====================
        // 校验新密码和确认密码必须一致
        if (!newPassword.equals(confirm_password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码与确认密码不一致");
        }
        // =================================================================

        // 4. 校验新密码格式
        if (!newPassword.matches("^[a-zA-Z0-9_]{6,32}$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码格式错误：6-32位字母/数字/下划线");
        }

        // 5. 新密码加密
        String newEncrypt = SecureUtil.sha256(newPassword + SALT);
        user.setPassword_hash(newEncrypt);

        // 6. 更新到数据库
        updateById(user);
        return ResultUtils.success(null);
    }

    @Override
    public BaseResponse<Null> logout() {
        return ResultUtils.success(null);
    }

}




