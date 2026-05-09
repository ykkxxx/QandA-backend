package com.ykx.backend.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.dto.admin.BanUserDTO;
import com.ykx.backend.model.dto.admin.BlockIpDTO;
import com.ykx.backend.model.dto.admin.BlockUsernameDTO;
import com.ykx.backend.model.dto.admin.UnbanUserDTO;
import com.ykx.backend.model.entity.AccessBlacklist;
import com.ykx.backend.service.SecurityAdminService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/security")
public class AdminSecurityController {

    @Resource
    private SecurityAdminService securityAdminService;

    @PostMapping("/ban/user")
    public BaseResponse<Void> banUser(@RequestBody BanUserDTO dto) {
        if (dto == null || StrUtil.isBlank(dto.getUserId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId 不能为空");
        }
        securityAdminService.banUser(dto.getUserId(), dto.getReason());
        return ResultUtils.success(null);
    }

    @PostMapping("/unban/user")
    public BaseResponse<Void> unbanUser(@RequestBody UnbanUserDTO dto) {
        if (dto == null || StrUtil.isBlank(dto.getUserId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId 不能为空");
        }
        securityAdminService.unbanUser(dto.getUserId());
        return ResultUtils.success(null);
    }

    @PostMapping("/blacklist/ip")
    public BaseResponse<Void> blockIp(@RequestBody BlockIpDTO dto) {
        if (dto == null || StrUtil.isBlank(dto.getIp())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "ip 不能为空");
        }
        securityAdminService.blockIp(dto.getIp(), dto.getReason());
        return ResultUtils.success(null);
    }

    @DeleteMapping("/blacklist/ip")
    public BaseResponse<Void> unblockIp(@RequestParam String ip) {
        if (StrUtil.isBlank(ip)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "ip 不能为空");
        }
        securityAdminService.unblockIp(ip);
        return ResultUtils.success(null);
    }

    @PostMapping("/blacklist/username")
    public BaseResponse<Void> blockUsername(@RequestBody BlockUsernameDTO dto) {
        if (dto == null || StrUtil.isBlank(dto.getUsername())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "username 不能为空");
        }
        securityAdminService.blockUsername(dto.getUsername(), dto.getReason());
        return ResultUtils.success(null);
    }

    @DeleteMapping("/blacklist/username")
    public BaseResponse<Void> unblockUsername(@RequestParam String username) {
        if (StrUtil.isBlank(username)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "username 不能为空");
        }
        securityAdminService.unblockUsername(username);
        return ResultUtils.success(null);
    }

    @GetMapping("/blacklist")
    public BaseResponse<Page<AccessBlacklist>> listBlacklist(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        if (size > 100) {
            size = 100;
        }
        return ResultUtils.success(securityAdminService.listBlacklist(page, size));
    }
}
