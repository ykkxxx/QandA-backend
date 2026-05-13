package com.ykx.backend.controller;

import com.ykx.backend.common.BaseResponse;
import com.ykx.backend.common.ResultUtils;
import com.ykx.backend.service.VectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 向量知识库控制器：文档入库、向量检索、嵌入调试。
 * 实际路径前缀见 server.servlet.context-path（当前为 /api），故此处映射 /vector → /api/vector。
 */
@RestController
@RequestMapping("/vector")
@RequiredArgsConstructor
public class VectorController {

    private final VectorService vectorService;

    /**
     * 文本内容直接入库（手动传入文本）
     *
     * @param userId     当前用户 id
     * @param content    文档正文
     * @param sourceFile 来源文件名
     */
    @PostMapping("/save/text")
    public BaseResponse<String> saveText(
            @RequestParam String userId,
            @RequestParam String content,
            @RequestParam String sourceFile
    ) {
        vectorService.saveToMilvus(userId, content, sourceFile);
        return ResultUtils.success("文档向量入库成功");
    }

    /**
     * 向量相似度检索
     *
     * @param userId   用户 id（数据隔离）
     * @param question 用户提问
     * @param topK     召回条数，默认 3（查询参数名必须为 topK，勿写成 tok 等否则不生效）
     */
    @GetMapping("/search")
    public BaseResponse<List<Map<String, Object>>> searchSimilar(
            @RequestParam String userId,
            @RequestParam String question,
            @RequestParam(name = "topK", defaultValue = "3") int topK
    ) {
        int k = topK;
        if (k < 1) {
            k = 3;
        } else if (k > 100) {
            k = 100;
        }
        List<Map<String, Object>> resultList = vectorService.search(userId, question, k);
        return ResultUtils.success(resultList);
    }

    /**
     * 将文字生成向量
     */
    @GetMapping("/embedding")
    public BaseResponse<List<Float>> getEmbedding(@RequestParam String text) {
        List<Float> embedding = vectorService.createEmbedding(text);
        return ResultUtils.success(embedding);
    }
}
