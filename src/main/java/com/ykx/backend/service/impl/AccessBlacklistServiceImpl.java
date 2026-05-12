package com.ykx.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ykx.backend.mapper.AccessBlacklistMapper;
import com.ykx.backend.model.entity.AccessBlacklist;
import com.ykx.backend.service.AccessBlacklistService;
import org.springframework.stereotype.Service;

/**
* @author 13797
* @description 针对表【access_blacklist(访问黑名单)】的数据库操作Service实现
* @createDate 2026-05-11 19:52:17
*/
@Service
public class AccessBlacklistServiceImpl extends ServiceImpl<AccessBlacklistMapper, AccessBlacklist>
    implements AccessBlacklistService{
}




