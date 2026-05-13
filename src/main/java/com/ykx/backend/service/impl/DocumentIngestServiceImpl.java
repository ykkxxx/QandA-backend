package com.ykx.backend.service.impl;

import com.ykx.backend.config.ChatAppProperties;
import com.ykx.backend.exception.BusinessException;
import com.ykx.backend.exception.ErrorCode;
import com.ykx.backend.model.vo.chat.DocumentUploadResultVO;
import com.ykx.backend.service.DocumentIngestService;
import com.ykx.backend.service.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestServiceImpl implements DocumentIngestService {

    private final VectorService vectorService;
    private final ChatAppProperties chatAppProperties;

    @Override
    public DocumentUploadResultVO ingest(MultipartFile file, String userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择要上传的文件");
        }
        long max = chatAppProperties.getDocumentMaxBytes();
        if (max > 0 && file.getSize() > max) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件过大，最大允许 " + (max / 1024 / 1024) + " MB");
        }

        String original = file.getOriginalFilename();
        String safeName = sanitizeFileName(original);
        String lower = safeName.toLowerCase(Locale.ROOT);

        String text;
        if (lower.endsWith(".txt")) {
            text = readTxt(file);
        } else if (lower.endsWith(".pdf")) {
            text = readPdf(file);
        } else {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 .txt 或 .pdf 文件");
        }

        text = normalizeText(text);
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件解析后无有效文本内容");
        }

        int chunks = vectorService.splitText(text).size();
        vectorService.saveToMilvus(userId, text, safeName);

        DocumentUploadResultVO vo = new DocumentUploadResultVO();
        vo.setSourceFile(safeName);
        vo.setChunkCount(chunks);
        vo.setMessage("文档已切片并向量入库");
        log.info("文档入库完成 userId={} file={} chunks={}", userId, safeName, chunks);
        return vo;
    }

    private static String sanitizeFileName(String original) {
        if (!StringUtils.hasText(original)) {
            return "upload.txt";
        }
        String n = original.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        if (slash >= 0 && slash < n.length() - 1) {
            n = n.substring(slash + 1);
        }
        n = n.replaceAll("[\r\n\t]", "_");
        if (n.length() > 200) {
            n = n.substring(n.length() - 200);
        }
        return n;
    }

    private static String readTxt(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String s = new String(bytes, StandardCharsets.UTF_8);
            if (s.startsWith("\uFEFF")) {
                s = s.substring(1);
            }
            return s;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取 TXT 失败：" + e.getMessage());
        }
    }

    private static String readPdf(MultipartFile file) {
        try (InputStream in = file.getInputStream(); PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "解析 PDF 失败：" + e.getMessage());
        }
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u0000', ' ').trim();
    }
}
