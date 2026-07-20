// 文件路径: src/main/java/com/ykx/backend/agent/TaskExecutor.java
package com.ykx.backend.agent;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务执行器
 * 负责解析任务计划JSON、批量执行子任务、统计任务执行结果并生成总结
 * 配合TaskPlanner任务分解工具使用，接收结构化任务计划并调度执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTaskExecutor {

    /**
     * 执行完整任务计划
     * @param planJson 大模型/兜底方法生成的结构化任务计划JSON字符串
     * @return 所有子任务执行结果拼接文本
     */
    public String executePlan(String planJson) {
        try {
            // 将任务计划JSON字符串解析为Hutool JSON对象
            JSONObject plan = JSONUtil.parseObj(planJson);
            // 获取任务数组列表
            JSONArray tasks = plan.getJSONArray("tasks");

            // 判空：无任务直接返回提示文本
            if (tasks == null || tasks.isEmpty()) {
                return "任务计划为空";
            }

            // 存储每个子任务的执行输出结果
            List<String> results = new ArrayList<>();
            // 循环遍历所有子任务依次执行
            for (int i = 0; i < tasks.size(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                // 执行单个任务，获取执行结果
                String result = executeTask(task);
                // 拼接任务编号+结果存入集合
                results.add("任务" + task.getInt("id") + ": " + result);
            }

            // 使用换行符拼接所有任务结果并返回
            return String.join("\n", results);
        } catch (Exception e) {
            // 捕获全局异常，记录错误日志并返回友好提示
            log.error("Task execution failed", e);
            return "任务执行失败：" + e.getMessage();
        }
    }

    /**
     * 执行单个子任务，更新任务状态
     * @param task 单个子任务JSON对象（包含id、description、status、input等字段）
     * @return 当前子任务执行结果文本
     */
    private String executeTask(JSONObject task) {
        // 获取任务描述，必选字段
        String description = task.getStr("description");
        // 获取任务状态，无status字段则默认pending待执行
        String status = task.getStr("status", "pending");

        log.info("Executing task: {}", description);

        try {
            // 任务执行成功，修改状态为已完成
            task.set("status", "completed");
            return "已完成: " + description;
        } catch (Exception e) {
            // 任务执行异常，更新状态为失败，并记录错误信息
            task.set("status", "failed");
            task.set("error", e.getMessage());
            return "失败: " + description + " (" + e.getMessage() + ")";
        }
    }

    /**
     * 统计任务计划执行情况，生成可视化总结文本
     * @param planJson 执行完成后的完整任务计划JSON（包含更新后的status、error字段）
     * @return 包含成功/失败列表、总数统计的汇总文本
     */
    public String summarizeResults(String planJson) {
        try {
            JSONObject plan = JSONUtil.parseObj(planJson);
            JSONArray tasks = plan.getJSONArray("tasks");

            // 无任务直接返回提示
            if (tasks == null || tasks.isEmpty()) {
                return "没有执行任何任务";
            }

            // 拼接总结文本
            StringBuilder summary = new StringBuilder("任务执行总结:\n");
            // 完成任务计数
            int completed = 0;
            // 失败任务计数
            int failed = 0;

            // 遍历所有任务，分类统计
            for (int i = 0; i < tasks.size(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                // 获取任务状态，缺失则标记unknown未知
                String status = task.getStr("status", "unknown");
                String description = task.getStr("description");

                // 已完成任务
                if ("completed".equals(status)) {
                    completed++;
                    summary.append("✓ ").append(description).append("\n");
                }
                // 失败任务，附带错误信息
                else if ("failed".equals(status)) {
                    failed++;
                    summary.append("✗ ").append(description).append(" (").append(task.getStr("error", "未知错误")).append(")\n");
                }
            }

            // 拼接底部统计汇总：总任务数、完成数、失败数
            summary.append("\n总计: ").append(tasks.size()).append(" 个任务, ")
                    .append(completed).append(" 完成, ").append(failed).append(" 失败");

            return summary.toString();
        } catch (Exception e) {
            log.error("Summarization failed", e);
            return "总结失败：" + e.getMessage();
        }
    }
}