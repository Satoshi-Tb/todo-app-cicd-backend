package com.example.taskapp.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.example.taskapp.model.Task;
import com.example.taskapp.model.TaskStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResp {
    private Long id;
    private String title;
    private String description;
    private TaskStatus status;
    private LocalDate dueDate;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public static TaskResp from(Task t) {
        if (t == null) return null;
        return TaskResp.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .status(t.getStatus())
                .dueDate(t.getDueDate())
                .version(t.getVersion())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}

