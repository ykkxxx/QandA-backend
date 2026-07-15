package com.ykx.backend.controller;

import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.model.vo.rag.DocumentVO;
import com.ykx.backend.service.VectorService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agent")
public class Agent {
    @Resource
    private VectorService vectorService;

    @GetMapping("/document/query")
    public List<DocumentVO> list(){
        String userId = UserContext.getUserId();
        return vectorService.listUserDocuments(userId);
    }

    @DeleteMapping("/document/delete")
    public BaseResponse<Boolean> delete(String documentId){
        String userId = UserContext.getUserId();
        boolean success = vectorService.deleteUserDocument(userId, documentId);
        return ResultUtils.success(success);
    }
}