package com.ykx.backend.service;

import com.ykx.backend.model.vo.chat.DocumentUploadResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档解析并向量入库（txt/pdf）。
 */
public interface DocumentIngestService {

    DocumentUploadResultVO ingest(MultipartFile file, String userId);
}
