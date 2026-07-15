package com.ykx.backend.agent.tools;

import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.common.UserContext;
import com.ykx.backend.model.vo.rag.DocumentVO;
import com.ykx.backend.service.VectorService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j  // 添加日志注解
@Component
@RequiredArgsConstructor
public class DocumentManagementTools {
    private final VectorService vectorService;

    /**
     * 获取用户的文档
     * @return
     */
    @Tool("当用户想查看自己上传的文档列表时调用。返回用户所有已入库的文档信息。")
    public String list() {
        String userId = UserContext.getUserId();
        List<DocumentVO> documents = vectorService.listUserDocuments(userId);

        // 把 List<DocumentVO> 转换成文本字符串
        if (documents.isEmpty()) {
            return "（您尚未上传任何文档）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【我的文档列表】\n");
        for (DocumentVO doc : documents) {
            sb.append("- ").append(doc.getDocumentName())
                    .append(" | 类型: ").append(doc.getDocumentType())
                    .append(" | 上传时间: ").append(doc.getUploadTime())
                    .append(" | 切片数: ").append(doc.getChunkTotal())
                    .append(" | 文档ID: ").append(doc.getDocumentId())
                    .append("\n");
        }
        return sb.toString();
    }
    /**
     * 删除用户的指定文档
     */
    @Tool("当用户想删除某个文档时调用。documentId为要删除的文档ID。")
    public String delete(String documentId) {
        String userId = UserContext.getUserId();
        boolean success = vectorService.deleteUserDocument(userId, documentId);

        // 返回文本描述
        if (success) {
            return "（文档已成功删除）";
        } else {
            return "（未找到该文档或删除失败）";
        }
    }
}
