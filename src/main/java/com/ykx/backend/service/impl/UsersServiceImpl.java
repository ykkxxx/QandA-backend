package com.ykx.backend.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
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
import com.ykx.backend.common.UserRoleConstants;
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
import com.ykx.backend.config.UploadProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.apache.ibatis.jdbc.Null;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private static final String USER_INFO =   "user:info:";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private BlacklistService blacklistService;

    @Resource
    private UploadProperties uploadProperties;

    /** 与 application.yml 中 server.servlet.context-path 一致，用于拼接浏览器可访问的头像 URL */
    @Value("${server.servlet.context-path:}")
    private String servletContextPath;

    private static final Set<String> AVATAR_ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final String JWT_SECRET = "Ykx_JWT_Secret_2025_123456789";

    @Override
    public void evictUserSession(String userId) {
        if (StrUtil.isBlank(userId)) {
            return;
        }
        try {
            stringRedisTemplate.delete(USER_LATEST_ACCESS_PREFIX + userId.trim());
            stringRedisTemplate.delete(USER_LATEST_REFRESH_PREFIX + userId.trim());
            stringRedisTemplate.delete(USER_INFO + userId.trim());
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
        //判断ip地址是否在黑名单中
        if (StrUtil.isNotBlank(clientIp) && blacklistService.isBlockedIp(clientIp)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "当前网络环境暂时无法登录");
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
        //更新登录时间
        user.setLast_login(new Date());
        this.updateById(user);
        //返回封装体
        //buildLoginData生成token
        LoginData<UsersLoginVO> loginData = buildLoginData(user);
        //将token存入redis
        bindLatestAccessToken(user.getUuid(), loginData.getAccess_token());
        bindLatestRefreshToken(user.getUuid(), loginData.getRefresh_token());
        return ResultUtils.success(loginData);
    }

    @Override
    public BaseResponse<UsersRegisterVO> register(UsersRegisterDTO usersRegisterDTO, String clientIp) {
        // 1. 获取用户名 密码 确认密码
        String username = usersRegisterDTO.getUsername();
        String password = usersRegisterDTO.getPassword();
        String confirmPassword = usersRegisterDTO.getConfirm_password();
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
        //4.校验ip是否合法
        if (StrUtil.isNotBlank(clientIp) && blacklistService.isBlockedIp(clientIp)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "当前网络环境暂时无法注册");
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
        user.setEmail(usersRegisterDTO.getEmail());
        user.setTelephone(usersRegisterDTO.getTelephone());
        user.setStatus(UserStatusConstants.NORMAL);
        user.setRole(UserRoleConstants.USER);
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
        //USER_LATEST_REFRESH_PREFIX   user:refresh:uuid : freshtoken
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
        payload.put("role", sessionUser.getRole() != null ? sessionUser.getRole() : UserRoleConstants.USER);
        payload.put("exp", System.currentTimeMillis() / 1000 + ACCESS_EXPIRE);
        String newAccessToken = JWTUtil.createToken(payload, JWT_SECRET.getBytes());
        bindLatestAccessToken(userId, newAccessToken);

        // 5. 封装返回
        LoginData<Null> loginData = new LoginData<>();
        loginData.setAccess_token(newAccessToken);
        loginData.setRefresh_token(refresh_token); // 保持原来的
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
        String userInfoKey = USER_INFO + userId;
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
            cacheVO.setAvatar(toClientAvatarUrl(cacheVO.getAvatar()));
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
        usersInfoVO.setAvatar(toClientAvatarUrl(usersInfoVO.getAvatar()));

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

    /**
     * 用户资料更新核心方法（同时支持：纯资料更新 + 头像上传更新）
     * @param updateDTO 前端传入的用户资料字段
     * @param avatarFile 头像文件（没有则为 null）
     * @return 更新后的用户信息 VO
     */
    @Override
    public BaseResponse<UsersUpdateVO> update(UsersUpdateDTO updateDTO, MultipartFile avatarFile) {

        // 1.校验参数
        if (updateDTO == null) {
            updateDTO = new UsersUpdateDTO();
        }

        // 2. 从当前登录上下文获取 userId
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "token 无效");
        }

        // 3. 查询当前用户是否存在
        Users existUser = getById(userId);
        if (existUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        // ==================== 头像/文件相关配置 ====================
        // 头像存储根目录
        Path avatarRoot = Paths.get(uploadProperties.getAvatarDir()).toAbsolutePath().normalize();
        // 需要删除的旧头像
        String oldAvatarToDelete = null;
        // 本次新保存的头像路径（用于更新失败时回滚删除）
        Path newlyWrittenAvatarPath = null;

        // ==================== 4. 用户资料安全更新（只更新非空字段） ====================

        // 更新用户名：校验格式 + 校验重复
        if (StrUtil.isNotBlank(updateDTO.getUsername())) {
            String newUsername = updateDTO.getUsername().trim();
            // 只有和原来不一样才需要校验
            if (!newUsername.equals(existUser.getUsername())) {
                // 用户名格式校验：3-20位字母/数字/下划线
                if (!newUsername.matches("^[a-zA-Z0-9_]{3,20}$")) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名格式错误：3-20位，仅允许字母、数字、下划线");
                }
                // 检查用户名是否被其他人占用
                LambdaQueryWrapper<Users> usernameTaken = new LambdaQueryWrapper<>();
                usernameTaken.eq(Users::getUsername, newUsername).ne(Users::getUuid, userId);
                if (this.count(usernameTaken) > 0) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名已被占用");
                }
                // 校验通过，设置新用户名
                existUser.setUsername(newUsername);
            }
        }

        // 更新邮箱
        if (updateDTO.getEmail() != null) {
            existUser.setEmail(StrUtil.trim(updateDTO.getEmail()));
        }

        // 更新手机
        if (updateDTO.getTelephone() != null) {
            existUser.setTelephone(StrUtil.trim(updateDTO.getTelephone()));
        }

        // 更新性别（0-未知 1-男 2-女）
        if (updateDTO.getGender() != null) {
            int g = updateDTO.getGender();
            if (g < 0 || g > 2) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "性别参数无效：仅支持 0-未知 1-男 2-女");
            }
            existUser.setGender(g);
        }

        // 更新个人简介
        if (updateDTO.getBio() != null) {
            existUser.setBio(updateDTO.getBio());
        }

        // ==================== 5. 头像上传处理 ====================
        if (avatarFile != null && !avatarFile.isEmpty()) {
            // 头像大小校验
            long maxBytes = uploadProperties.getMaxAvatarBytes();
            if (maxBytes > 0 && avatarFile.getSize() > maxBytes) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像文件过大");
            }

            // 获取文件后缀并转小写
            String ext = StrUtil.blankToDefault(FileUtil.extName(avatarFile.getOriginalFilename()), "").toLowerCase(Locale.ROOT);
            // 后缀白名单校验
            if (!AVATAR_ALLOWED_EXT.contains(ext)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 jpg、jpeg、png、gif、webp 图片");
            }

            // 文件类型校验（必须是图片）
            String contentType = avatarFile.getContentType();
            if (StrUtil.isNotBlank(contentType) && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型必须是图片");
            }

            // 创建头像存储目录（不存在则创建）
            try {
                Files.createDirectories(avatarRoot);
            } catch (Exception e) {
                log.warn("创建头像目录失败: {}", e.getMessage());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像存储目录不可用");
            }

            // 生成唯一文件名，防止重复/覆盖
            String storedName = IdUtil.fastSimpleUUID() + "." + ext;
            // 拼接目标路径
            Path target = avatarRoot.resolve(storedName).normalize();

            // 安全校验：防止路径穿越攻击
            if (!target.startsWith(avatarRoot)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法文件名");
            }

            // 保存文件到磁盘
            try (InputStream in = avatarFile.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                log.warn("保存头像失败: {}", e.getMessage());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像保存失败");
            }

            // 记录旧头像，用于后续删除
            oldAvatarToDelete = existUser.getAvatar();
            // 设置新头像路径
            existUser.setAvatar(publicUploadedAvatarPath(storedName));
            // 记录本次新文件路径（用于回滚）
            newlyWrittenAvatarPath = target;
        }
        // 如果前端手动传了 avatar 字段（不是上传文件，是网络地址）
        else if (updateDTO.getAvatar() != null) {
            existUser.setAvatar(StrUtil.trim(updateDTO.getAvatar()));
        }

        // ==================== 6. 执行数据库更新 ====================
        boolean updateSuccess = updateById(existUser);
        // 更新失败：回滚——删除刚才保存的新头像
        if (!updateSuccess) {
            if (newlyWrittenAvatarPath != null) {
                try {
                    Files.deleteIfExists(newlyWrittenAvatarPath);
                } catch (Exception ignore) {
                    log.warn("回滚删除新头像文件失败");
                }
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败");
        }

        // ==================== 7. 更新成功：删除旧头像 ====================
        if (avatarFile != null && !avatarFile.isEmpty()) {
            deleteStoredAvatarIfUnderUpload(oldAvatarToDelete, avatarRoot);
        }

        // ==================== 8. 清除用户缓存，保证下次查询是最新数据 ====================
        try {
            stringRedisTemplate.delete("user:info:" + userId);
        } catch (Exception e) {
            log.warn("清除用户信息缓存失败: {}", e.getMessage());
        }

        // ==================== 9. 封装返回结果 ====================
        UsersUpdateVO updateVO = new UsersUpdateVO();
        BeanUtil.copyProperties(existUser, updateVO);
        // 拼接前端可访问的完整头像 URL
        updateVO.setAvatar(toClientAvatarUrl(updateVO.getAvatar()));

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
        //sso:bridge:d4b2f3e3a1c94876b8e7f9a012345678  ：  user_id
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
           //用过立刻删除！
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
        return ResultUtils.success(loginData);
    }

    private void deleteStoredAvatarIfUnderUpload(String avatarUrl, Path avatarRoot) {
        String name = extractUploadedAvatarFileName(avatarUrl);
        if (name == null) {
            return;
        }
        try {
            Path p = avatarRoot.resolve(name).normalize();
            if (p.startsWith(avatarRoot)) {
                Files.deleteIfExists(p);
            }
        } catch (Exception e) {
            log.warn("删除旧头像文件失败: {}", e.getMessage());
        }
    }

    /** 去掉尾部斜杠；空或 "/" 视为无前缀 */
    private String normalizedContextPath() {
        String cp = servletContextPath == null ? "" : servletContextPath.trim();
        if (cp.isEmpty() || "/".equals(cp)) {
            return "";
        }
        if (cp.endsWith("/")) {
            return cp.substring(0, cp.length() - 1);
        }
        return cp;
    }

    /** 写入数据库的本站头像路径（含 context-path，便于前端直接拼同源 URL） */
    private String publicUploadedAvatarPath(String storedFileName) {
        return normalizedContextPath() + "/files/avatars/" + storedFileName;
    }

    /**
     * 返回给前端的头像地址：本站相对路径会补上 context-path；
     * 历史数据若仅存 /files/avatars/xxx 也会自动补上 /api 等前缀。
     */
    private String toClientAvatarUrl(String raw) {
        if (StrUtil.isBlank(raw)) {
            return raw;
        }
        String t = raw.trim();
        if (t.startsWith("http://") || t.startsWith("https://")) {
            return t;
        }
        String ctx = normalizedContextPath();
        String uploadPrefix = "/files/avatars/";
        if (ctx.isEmpty()) {
            return t;
        }
        if (t.startsWith(ctx + uploadPrefix)) {
            return t;
        }
        if (t.startsWith(uploadPrefix)) {
            return ctx + t;
        }
        return t;
    }

    /** 从本站头像 URL 解析出磁盘文件名；无法识别则返回 null */
    private String extractUploadedAvatarFileName(String avatarUrl) {
        if (StrUtil.isBlank(avatarUrl)) {
            return null;
        }
        String ctx = normalizedContextPath();
        String withCtx = ctx + "/files/avatars/";
        if (StrUtil.isNotBlank(ctx) && avatarUrl.startsWith(withCtx)) {
            return safeAvatarFileName(avatarUrl.substring(withCtx.length()));
        }
        if (avatarUrl.startsWith("/files/avatars/")) {
            return safeAvatarFileName(avatarUrl.substring("/files/avatars/".length()));
        }
        return null;
    }

    private String safeAvatarFileName(String name) {
        if (name.isEmpty() || name.contains("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            return null;
        }
        return name;
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
    //把用户最新的 refresh_token 存到 Redis 里
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
    //  登录成功后，给用户生成一套完整的登录信息
    private LoginData<UsersLoginVO> buildLoginData(Users user) {
        //公共数据
        Map<String, Object> commonPayload = new HashMap<>();
        commonPayload.put("userId", user.getUuid());
        commonPayload.put("username", user.getUsername());
        commonPayload.put("role", user.getRole() != null ? user.getRole() : UserRoleConstants.USER);
        //复制公共数据 → 再加自己的过期时间 access的map
        Map<String, Object> accessPayload = new HashMap<>(commonPayload);
        accessPayload.put("exp", System.currentTimeMillis() / 1000 + ACCESS_EXPIRE);

        String accessToken = JWTUtil.createToken(accessPayload, JWT_SECRET.getBytes());

        //refreshmap
        //为什么要这么写？
        //因为 accessToken 和 refreshToken 的过期时间不一样！
        Map<String, Object> refreshPayload = new HashMap<>(commonPayload);
        refreshPayload.put("exp", System.currentTimeMillis() / 1000 + REFRESH_EXPIRE);

        String refreshToken = JWTUtil.createToken(refreshPayload, JWT_SECRET.getBytes());

        UsersLoginVO usersVO = BeanUtil.copyProperties(user, UsersLoginVO.class);
        usersVO.setAvatar(toClientAvatarUrl(user.getAvatar()));
        LoginData<UsersLoginVO> loginData = new LoginData<>();
        loginData.setAccess_token(accessToken);
        loginData.setRefresh_token(refreshToken);
        loginData.setExpires_in((int) ACCESS_EXPIRE);
        loginData.setToken_type("Bearer");
        loginData.setUser(usersVO);
        return loginData;
    }

}




