package com.ykx.backend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.dto.LoginFormDTO;
import com.ykx.backend.model.entity.User;
import com.ykx.backend.model.vo.UserVO;
import com.ykx.backend.service.UserService;
import com.ykx.backend.mapper.UserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
* @author 13797
* @description 针对表【user(用户表)】的数据库操作Service实现
* @createDate 2026-03-05 19:31:21
*/
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserMapper userMapper;
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    @Override
    public void sendCode(String phone) {
        //1.校验手机号格式
        if(!isValidPhone(phone)){
            log.error("手机格式错误,输入：{}",phone);
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //2.生成验证码
        Random random = new Random();
        String code = String.format("%06d",random.nextInt(1000000));
        //3.将验证码存入redis
        String redisKeyPrefix= "user:code:";
        stringRedisTemplate.opsForValue().set(redisKeyPrefix + phone,code,2, TimeUnit.MINUTES);
        log.info("模拟发短信,给手机号：{}发送了验证码：{}",phone,code);
    }

    @Override
    public String login(LoginFormDTO loginFormDTO) {
        //1.校验手机号和验证码格式
        if(!isValidPhone(loginFormDTO.getPhone())){
            log.error("手机号{}格式不对",loginFormDTO.getPhone());
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        //2.校验验证码是否匹配
        String redisPrefix = "user:code:";
        String redisCode = stringRedisTemplate.opsForValue().get(redisPrefix+loginFormDTO.getPhone());
        if(redisCode == null || !redisCode.equals(loginFormDTO.getCode())) {
            log.error("验证码有误或已过期");

            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码有误或已过期");
        }
        //3.查询用户是否存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone,loginFormDTO.getPhone());
        User user = userMapper.selectOne(wrapper);
        //4.创建脱敏的用户
        UserVO userVO = new UserVO();
        //用户不存在 帮他注册
        if(user == null) {
            log.info("用户不存在");
            User usernew = new User(); //
            usernew.setPhone(loginFormDTO.getPhone());
            usernew.setPassword("123456789");
            usernew.setUsername("user_" + RandomUtil.randomString(10)); // 随机生成一个昵称
            BeanUtil.copyProperties(usernew,userVO);
            int result = userMapper.insert(usernew);
            if(result == 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,"数据库异常");
            }
        }
        //5.返回token
        String token = UUID.randomUUID().toString().replace("-", "");
        log.info("token:{}",token);
        //6.脱敏的用户转成map
        Map<String, Object> userMap = BeanUtil.beanToMap(userVO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue != null ? fieldValue.toString() : ""));
        //将数据存入redis
        String redisKeyPrefix = "user:token";
        String redisKey = redisKeyPrefix + token;
        stringRedisTemplate.opsForHash().putAll(redisKey,userMap);
        //设置过期时间
        stringRedisTemplate.expire(redisKey,30,TimeUnit.MINUTES);
        // 7. 阅后即焚：删除刚才用过的验证码
        stringRedisTemplate.delete("user:code:"+ loginFormDTO.getPhone());
        return token;
    }

    // 封装手机号校验方法（复用性更高）
    private boolean isValidPhone(String phone) {
        // 先判空，再匹配正则
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }
}





