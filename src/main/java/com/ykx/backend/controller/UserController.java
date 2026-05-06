package com.ykx.backend.controller;

import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.model.dto.LoginFormDTO;
import com.ykx.backend.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController("/user")
@Slf4j
public class UserController {
    @Resource
    private UserService userService;
    @PostMapping("code")
    public BaseResponse<String> sendCode(@RequestParam  String phone){
        userService.sendCode(phone);
        return ResultUtils.success(null);
    }
    @PostMapping("/login")
    public BaseResponse<String> login(@RequestBody LoginFormDTO loginFormDTO){
        String token = userService.login(loginFormDTO);
        return ResultUtils.success(token);
    }
}
