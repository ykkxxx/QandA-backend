package com.ykx.backend.controller;

import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.dto.rag.RagQueryDTO;
import com.ykx.backend.model.vo.rag.RagContextVO;
import com.ykx.backend.service.RagService;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG：召回 + 重排 + 上下文（用户 id 取自登录态，与消息接口一致）。
 */
@RestController
@RequestMapping("/rag")
@Validated
public class RagController {

    @Resource
    private RagService ragService;

    /**
     * 构建检索上下文与建议的大模型用户侧提示词（不直接调用大模型）。
     */
    @PostMapping("/context")
    public BaseResponse<RagContextVO> buildContext(@RequestBody RagQueryDTO dto) {
        if (dto == null || StrUtil.isBlank(dto.getQuestion())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "question 不能为空");
        }
        String userId = UserContext.getUserId();
        RagContextVO vo = ragService.buildContext(userId, dto.getQuestion());
        return ResultUtils.success(vo);
    }
}
