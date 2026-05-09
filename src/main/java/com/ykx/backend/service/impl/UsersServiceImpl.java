package com.ykx.backend.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.common.UserStatusConstants;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.dto.UsersLoginDTO;
import com.ykx.backend.model.dto.UsersRegisterDTO;
import com.ykx.backend.model.dto.UsersUpdateDTO;
import com.ykx.backend.model.entity.Users;
import com.ykx.backend.model.vo.LoginData;
import com.ykx.backend.model.vo.user.SsoCodeVO;
import com.ykx.backend.model.vo.user.UsersInfoVO;
import com.ykx.backend.model.vo.user.UsersLoginVO;
import com.ykx.backend.model.vo.user.UsersRegisterVO;
import com.ykx.backend.model.vo.user.UsersUpdateVO;
import com.ykx.backend.service.BlacklistService;
import com.ykx.backend.service.UsersService;
import com.ykx.backend.mapper.UsersMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.jdbc.Null;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
* @author 13797
* @description 针对表【users(用户表)】的数据库操作Service实现
* @createDate 2026-05-08 10:05:28
*/
@Slf4j
@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users>
    implements UsersService{
    // 密码加密盐
    private static final String SALT = "Ykx_2025_Rag_System_Salt";

    // Access Token 有效期：2小时
    private static final long ACCESS_EXPIRE = 7200L;
    // Refresh Token 有效期：7天
    private static final long REFRESH_EXPIRE = 604800L;

    private static final String SSO_CODE_PREFIX = "sso:bridge:";
    private static final long SSO_CODE_EXPIRE_SECONDS = 120L;

    /** Redis：当前生效的 access_token（拦截器比对） */
    private static final String USER_LATEST_ACCESS_PREFIX = "user:token:";
    /** Redis：当前生效的 refresh_token（刷新接口比对；新登录会覆盖，旧 refresh 即作废） */
    private static final String USER_LATEST_REFRESH_PREFIX = "user:refresh:";
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private BlacklistService blacklistService;

    // 你的 JWT 密钥（和业务代码一致）
    private static final String JWT_SECRET = "Ykx_JWT_Secret_2025_123456789";

    @Override
    public void evictUserSession(String userId) {
        if (StrUtil.isBlank(userId)) {
            return;
        }
        try {
            stringRedisTemplate.delete(USER_LATEST_ACCESS_PREFIX + userId.trim());
            stringRedisTemplate.delete(USER_LATEST_REFRESH_PREFIX + userId.trim());
            stringRedisTemplate.delete("user:info:" + userId.trim());
        } catch (Exception e) {
            log.warn("清理用户会话 Redis 失败: {}", e.getMessage());
        }
    }

    @Override
    public BaseResponse<LoginData<UsersLoginVO>> login(UsersLoginDTO usersLoginDTO, String clientIp) {
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

        if (StrUtil.isNotBlank(clientIp) && blacklistService.isBlockedIp(clientIp)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "当前网络环境暂时无法登录");
        }
        if (blacklistService.isBlockedUsername(username)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该用户名暂时无法登录");
        }

        // 4. 校验用户名和密码是否匹配
        LambdaQueryWrapper<Users> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Users::getUsername, username);
        Users user = getOne(wrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR,"用户名或密码错误");
        }

        if (!UserStatusConstants.isNormal(user.getStatus()) || blacklistService.isBlockedUserId(user.getUuid())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该账号已被封禁，无法登录");
        }

        // 密码比对
        String encryptPassword = SecureUtil.sha256(password + SALT);
        if (!encryptPassword.equals(user.getPassword_hash())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名或密码错误");
        }

        user.setLast_login(new Date());
        this.updateById(user);
        LoginData<UsersLoginVO> loginData = buildLoginData(user);
        bindLatestAccessToken(user.getUuid(), loginData.getAccess_token());
        bindLatestRefreshToken(user.getUuid(), loginData.getRefresh_token());
        return ResultUtils.success(loginData);
    }

    @Override
    public BaseResponse<UsersRegisterVO> register(UsersRegisterDTO usersRegisterVODTO, String clientIp) {
        // 1. 获取用户名 密码 确认密码
        String username = usersRegisterVODTO.getUsername();
        String password = usersRegisterVODTO.getPassword();
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

        if (StrUtil.isNotBlank(clientIp) && blacklistService.isBlockedIp(clientIp)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "当前网络环境暂时无法注册");
        }
        if (blacklistService.isBlockedUsername(username)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该用户名暂时无法注册");
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
        // 6. 构建用户实体（必须补全默认字段，否则入库报错）
        Users user = new Users();
        user.setUsername(username);
        user.setPassword_hash(encryptPassword); // 存密文
        user.setEmail(usersRegisterVODTO.getEmail());
        user.setTelephone(usersRegisterVODTO.getTelephone());
        user.setStatus(UserStatusConstants.NORMAL);
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

        String storedRefresh = null;
        try {
            storedRefresh = stringRedisTemplate.opsForValue().get(USER_LATEST_REFRESH_PREFIX + userId);
        } catch (Exception e) {
            log.warn("读取 refresh 会话 Redis 失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "会话服务不可用");
        }
        if (StrUtil.isBlank(storedRefresh) || !storedRefresh.equals(refresh_token)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "账号已在其他设备登录，请重新登录");
        }

        Users sessionUser = getById(userId);
        if (sessionUser == null || !UserStatusConstants.isNormal(sessionUser.getStatus())
                || blacklistService.isBlockedUserId(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该账号已被封禁，请重新登录");
        }

        // 4. 只生成 新 access_token（关键！）
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("exp", System.currentTimeMillis() / 1000 + ACCESS_EXPIRE);
        String newAccessToken = JWTUtil.createToken(payload, JWT_SECRET.getBytes());
        bindLatestAccessToken(userId, newAccessToken);

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
        // 1.从当前线程上下文获取登录用户ID（拦截器解析token后存入）
        String userId = UserContext.getUserId();
        // 判断userId是否为空 = 拦截器没有存入、token解析失败、未登录
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "token 解析失败");
        }

        // 2.拼接用户缓存key（固定格式：user:info:用户uuid）
        String userInfoKey = "user:info:" + userId;
        String cacheStr = null;
        try {
            // 3.尝试从Redis读取用户缓存
            cacheStr = stringRedisTemplate.opsForValue().get(userInfoKey);
        } catch (Exception e) {
            // Redis连接失败/服务未启动，打印警告，不中断程序，降级走数据库查询
            log.warn("读取用户信息缓存失败，将查询数据库: {}", e.getMessage());
        }

        // 4.判断缓存是否存在
        if (StrUtil.isNotBlank(cacheStr)) {
            // 将JSON字符串转为实体VO
            UsersInfoVO cacheVO = JSONUtil.toBean(cacheStr, UsersInfoVO.class);
            // 缓存命中，直接返回，无需查询数据库
            return ResultUtils.success(cacheVO);
        }

        // 5.缓存未命中，查询数据库
        Users user = getById(userId);
        // 数据库无对应用户，抛出异常
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        // 6.数据库查询成功，封装为脱敏VO（防止敏感字段外泄）
        UsersInfoVO usersInfoVO = new UsersInfoVO();
        BeanUtil.copyProperties(user, usersInfoVO);

        try {
            // 7.将用户信息写入Redis缓存，过期时间15分钟
            stringRedisTemplate.opsForValue().set(userInfoKey, JSONUtil.toJsonStr(usersInfoVO), 15, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 写入缓存失败只警告，不影响正常业务返回
            log.warn("写入用户信息缓存失败: {}", e.getMessage());
        }

        // 8.返回用户信息
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
        evictUserSession(UserContext.getUserId());
        return ResultUtils.success(null);
    }

    @Override
    public BaseResponse<SsoCodeVO> createSsoBridgeCode() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未登录");
        }
        String code = IdUtil.fastSimpleUUID();
        String redisKey = SSO_CODE_PREFIX + code;
        try {
            stringRedisTemplate.opsForValue().set(redisKey, userId, SSO_CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("SSO 写入 Redis 失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "SSO 暂不可用");
        }
        SsoCodeVO vo = new SsoCodeVO();
        vo.setCode(code);
        vo.setExpires_in((int) SSO_CODE_EXPIRE_SECONDS);
        return ResultUtils.success(vo);
    }

    @Override
    public BaseResponse<LoginData<UsersLoginVO>> exchangeSsoCode(String code) {
        if (StrUtil.isBlank(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "code 不能为空");
        }
        String redisKey = SSO_CODE_PREFIX + code.trim();
        String userId = null;
        try {
            userId = stringRedisTemplate.opsForValue().get(redisKey);
            if (StrUtil.isNotBlank(userId)) {
                stringRedisTemplate.delete(redisKey);
            }
        } catch (Exception e) {
            log.warn("SSO 校验 Redis 失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "SSO 暂不可用");
        }

        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效或已过期的 SSO 凭证");
        }
        Users user = getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        if (!UserStatusConstants.isNormal(user.getStatus()) || blacklistService.isBlockedUserId(user.getUuid())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该账号已被封禁，无法使用单点登录");
        }
        LoginData<UsersLoginVO> loginData = buildLoginData(user);
        bindLatestAccessToken(user.getUuid(), loginData.getAccess_token());
        bindLatestRefreshToken(user.getUuid(), loginData.getRefresh_token());
        return ResultUtils.success(loginData);
    }

    private void bindLatestAccessToken(String userId, String accessToken) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(accessToken)) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    USER_LATEST_ACCESS_PREFIX + userId,
                    accessToken,
                    ACCESS_EXPIRE,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入会话 Redis 失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "会话服务不可用");
        }
    }

    private void bindLatestRefreshToken(String userId, String refreshToken) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(refreshToken)) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    USER_LATEST_REFRESH_PREFIX + userId,
                    refreshToken,
                    REFRESH_EXPIRE,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入 refresh 会话 Redis 失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "会话服务不可用");
        }
    }

    private LoginData<UsersLoginVO> buildLoginData(Users user) {
        Map<String, Object> commonPayload = new HashMap<>();
        commonPayload.put("userId", user.getUuid());
        commonPayload.put("username", user.getUsername());
        Map<String, Object> accessPayload = new HashMap<>(commonPayload);
        accessPayload.put("exp", System.currentTimeMillis() / 1000 + ACCESS_EXPIRE);
        String accessToken = JWTUtil.createToken(accessPayload, JWT_SECRET.getBytes());
        Map<String, Object> refreshPayload = new HashMap<>(commonPayload);
        refreshPayload.put("exp", System.currentTimeMillis() / 1000 + REFRESH_EXPIRE);
        String refreshToken = JWTUtil.createToken(refreshPayload, JWT_SECRET.getBytes());
        UsersLoginVO usersVO = BeanUtil.copyProperties(user, UsersLoginVO.class);
        LoginData<UsersLoginVO> loginData = new LoginData<>();
        loginData.setAccess_token(accessToken);
        loginData.setRefresh_token(refreshToken);
        loginData.setExpires_in((int) ACCESS_EXPIRE);
        loginData.setToken_type("Bearer");
        loginData.setUser(usersVO);
        return loginData;
    }

}




