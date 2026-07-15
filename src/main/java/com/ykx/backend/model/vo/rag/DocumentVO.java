package com.ykx.backend.model.vo.rag;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentVO {
    /** 文档唯一ID（一份文档所有切片共用） */
    private String documentId;
    /** 文档原始文件名 */
    private String documentName;
    /** 文档上传时间 */
    private String uploadTime;
    /** 该文档拆分的切片总数（可选） */
    private Integer chunkTotal;
    private String documentType;

}
