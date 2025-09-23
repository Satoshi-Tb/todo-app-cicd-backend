package com.example.taskapp.model;

import java.time.Instant;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {
    private Long id;
    private String title;
    private String description;
    private TaskStatus status;
    private LocalDate dueDate;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}

