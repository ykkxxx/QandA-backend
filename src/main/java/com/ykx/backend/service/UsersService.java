package com.ykx.backend.service;

import com.fasterxml.jackson.databind.ser.Serializers;
import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.model.dto.UsersLoginDTO;
import com.ykx.backend.model.dto.UsersRegisterDTO;
import com.ykx.backend.model.dto.UsersUpdateDTO;
import com.ykx.backend.model.entity.Users;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ykx.backend.model.vo.LoginData;
import com.ykx.backend.model.vo.user.UsersInfoVO;
import com.ykx.backend.model.vo.user.UsersLoginVO;
import com.ykx.backend.model.vo.user.UsersRegisterVO;
import com.ykx.backend.model.vo.user.UsersUpdateVO;
import org.apache.ibatis.jdbc.Null;

/**
* @author 13797
* @description 针对表【users(用户表)】的数据库操作Service
* @createDate 2026-05-08 10:05:28
*/
public interface UsersService extends IService<Users> {
    //用户登录
    BaseResponse<LoginData<UsersLoginVO>> login(UsersLoginDTO usersLoginDTO);
    //用户注册
    BaseResponse<UsersRegisterVO> register(UsersRegisterDTO usersRegisterVODTO);
    //刷新token
    BaseResponse<LoginData<Null>>  refreshToken(String refresh_token);
    //获取用户信息
    BaseResponse<UsersInfoVO> getUserInfo();
    //更新用户信息
    BaseResponse<UsersUpdateVO> update(UsersUpdateDTO updateDTO);
    //重置密码
    BaseResponse<Null> resetPassword(String oldPassword,String newPassword,String confirm_password);
    //用户注销
    BaseResponse<Null> logout();
}
