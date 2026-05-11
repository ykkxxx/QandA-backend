package com.ykx.backend.service;

import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.model.dto.UsersLoginDTO;
import com.ykx.backend.model.dto.UsersRegisterDTO;
import com.ykx.backend.model.dto.UsersUpdateDTO;
import com.ykx.backend.model.entity.Users;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ykx.backend.model.vo.LoginData;
import com.ykx.backend.model.vo.user.UsersInfoVO;
import com.ykx.backend.model.vo.user.UsersLoginVO;
import com.ykx.backend.model.vo.user.SsoCodeVO;
import com.ykx.backend.model.vo.user.UsersRegisterVO;
import com.ykx.backend.model.vo.user.UsersUpdateVO;
import org.apache.ibatis.jdbc.Null;
import org.springframework.web.multipart.MultipartFile;

/**
* @author 13797
* @description 针对表【users(用户表)】的数据库操作Service
* @createDate 2026-05-08 10:05:28
*/
public interface UsersService extends IService<Users> {
    /** 踢下线：清理 Redis 中的 access/refresh 及用户信息缓存 */
    void evictUserSession(String userId);

    //用户登录
    BaseResponse<LoginData<UsersLoginVO>> login(UsersLoginDTO usersLoginDTO, String clientIp);
    //用户注册
    BaseResponse<UsersRegisterVO> register(UsersRegisterDTO usersRegisterVODTO, String clientIp);
    //刷新token
    BaseResponse<LoginData<Null>>  refreshToken(String refresh_token);
    //获取用户信息
    BaseResponse<UsersInfoVO> getUserInfo();
    /**
     * 更新用户信息；{@code avatarFile} 非空时保存本地头像并写入 {@code users.avatar}（含 {@code server.servlet.context-path}，如 /api/files/avatars/xxx.png），优先级高于 DTO 中的 avatar 字符串。
     */
    BaseResponse<UsersUpdateVO> update(UsersUpdateDTO updateDTO, MultipartFile avatarFile);
    //重置密码
    BaseResponse<Null> resetPassword(String oldPassword,String newPassword,String confirm_password);
    //用户注销
    BaseResponse<Null> logout();

    /** 已登录用户生成 SSO 一次性票据（另一站点用 exchange 换 token） */
    BaseResponse<SsoCodeVO> createSsoBridgeCode();

    /** 用一次性票据换取与本站登录相同结构的 token（两个站点可同时持有各自 JWT） */
    BaseResponse<LoginData<UsersLoginVO>> exchangeSsoCode(String code);
}
