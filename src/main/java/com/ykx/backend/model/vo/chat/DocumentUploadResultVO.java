package com.ykx.backend.model.vo.chat;

import lombok.Data;

@Data
public class DocumentUploadResultVO {

    private String sourceFile;
    private int chunkCount;
    private String message;
}
