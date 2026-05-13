package com.ykx.backend.controller;

import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.dto.MessageSendRequestDTO;
import com.ykx.backend.model.vo.chat.DocumentUploadResultVO;
import com.ykx.backend.model.vo.session.MessageSendResponseVO;
import com.ykx.backend.service.ChatService;
import com.ykx.backend.service.DocumentIngestService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.StringJoiner;

/**
 * 统一聊天入口：提问作答（会话持久化）、知识库文档上传。
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @Resource
    private DocumentIngestService documentIngestService;

    /**
     * 发送提问：RAG + 大模型 + 会话与消息落库（与 /message/send 能力一致，走 {@link ChatService}）。
     */
    @PostMapping("/send")
    @Validated
    public BaseResponse<MessageSendResponseVO> send(@Valid @RequestBody MessageSendRequestDTO dto) {
        return chatService.sendMessage(dto);
    }

    /**
     * 上传 txt/pdf：解析全文后按现有向量配置切片、嵌入并写入 Milvus。
     * <p>入参仅使用 {@link MultipartHttpServletRequest}，由 Spring 在解析参数阶段完成 multipart 绑定；
     * 若使用 {@code HttpServletRequest} + {@code @RequestParam MultipartFile}，在 FormContentFilter 等场景下
     * 请求可能尚未包装为 Multipart 类型，导致始终取不到文件。</p>
     */
    @PostMapping("/document")
    public BaseResponse<DocumentUploadResultVO> uploadDocument(MultipartHttpServletRequest request) {
        MultipartFile resolved = resolveUploadFile(request);
        if (resolved == null || resolved.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, describeMissingFile(request));
        }
        String userId = UserContext.getUserId();
        DocumentUploadResultVO vo = documentIngestService.ingest(resolved, userId);
        return ResultUtils.success(vo);
    }

    private static MultipartFile resolveUploadFile(MultipartHttpServletRequest request) {
        MultipartFile named = request.getFile("file");
        if (named != null && !named.isEmpty()) {
            return named;
        }
        for (MultipartFile p : request.getFileMap().values()) {
            if (p != null && !p.isEmpty()) {
                return p;
            }
        }
        return named;
    }

    private static String describeMissingFile(MultipartHttpServletRequest request) {
        if (!request.getFileMap().isEmpty()) {
            StringJoiner j = new StringJoiner(", ");
            request.getFileMap().forEach((k, v) -> j.add(k + (v != null && !v.isEmpty() ? "" : "(空)")));
            return "请选择要上传的文件。当前收到的文件字段名：" + j + "。请将表单项名称设为 file。";
        }
        String ct = request.getContentType();
        return "请选择要上传的文件。Content-Type=" + (ct != null ? ct : "null")
                + "。请使用 Body→form-data→类型选「文件」、字段名 file；不要手动填写 multipart 的 Content-Type。";
    }
}
