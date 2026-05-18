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

    /**
     * 上传文档并解析、切片、存入向量库（核心入口方法）
     * @param file 上传的文件（pdf/txt）
     * @param userId 当前登录用户ID
     * @return 上传结果（文件名、切片数量）
     */
    @Override
    public DocumentUploadResultVO ingest(MultipartFile file, String userId) {
        // 1. 判断文件是否为空
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择要上传的文件");
        }

        // 2. 判断文件大小是否超出限制
        long max = chatAppProperties.getDocumentMaxBytes();
        if (max > 0 && file.getSize() > max) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件过大，最大允许 " + (max / 1024 / 1024) + " MB");
        }

        // 3. 获取原始文件名，并进行安全化处理（防止非法路径/字符）
        String original = file.getOriginalFilename();
        String safeName = sanitizeFileName(original);

        // 4. 转小写，用于判断文件类型
        String lower = safeName.toLowerCase(Locale.ROOT);

        String text;
        // 5. 根据文件类型，读取文本内容
        if (lower.endsWith(".txt")) {
            // 读取 TXT 文件
            text = readTxt(file);
        } else if (lower.endsWith(".pdf")) {
            // 读取 PDF 文件内容
            text = readPdf(file);
        } else {
            // 不支持的文件类型
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 .txt 或 .pdf 文件");
        }

        // 6. 清洗文本（去掉非法字符、空格等）
        text = normalizeText(text);

        // 7. 判断解析后是否有有效内容
        if (!StringUtils.hasText(text)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件解析后无有效文本内容");
        }

        // 8. 文本切片（分块），并获取切片数量
        int chunks = vectorService.splitText(text).size();

        // 9. 将切片后的文本向量化，存入 Milvus 向量库
        vectorService.saveToMilvus(userId, text, safeName);

        // 10. 封装返回结果
        DocumentUploadResultVO vo = new DocumentUploadResultVO();
        vo.setSourceFile(safeName);        // 安全文件名
        vo.setChunkCount(chunks);         // 切片数量
        vo.setMessage("文档已切片并向量入库");
        log.info("文档入库完成 userId={} file={} chunks={}", userId, safeName, chunks);

        return vo;
    }
    //清洗文件名
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
    //读取 TXT 文件内容
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
    //读取 PDF 文件内容
    private static String readPdf(MultipartFile file) {
        try (InputStream in = file.getInputStream(); PDDocument doc = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "解析 PDF 失败：" + e.getMessage());
        }
    }
    //去掉空字符 \u0000
    //去前后空格
    //保证文本干净可入库
    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u0000', ' ').trim();
    }
}
