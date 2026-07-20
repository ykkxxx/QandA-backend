// 文件路径: src/main/java/com/ykx/backend/model/dto/TaskPlanDTO.java

package com.ykx.backend.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class TaskPlanDTO {

    private List<TaskItem> tasks;
    private String summary;

    @Data
    public static class TaskItem {
        private Integer id;
        private String description;
        private String status;
        private String input;
        private String output;
        private String error;
    }
}