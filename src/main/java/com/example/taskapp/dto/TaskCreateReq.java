package com.example.taskapp.dto;

import java.time.LocalDate;

import com.example.taskapp.model.TaskStatus;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TaskCreateReq {
    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 4000)
    private String description;

    @NotNull
    private TaskStatus status;

    @FutureOrPresent
    private LocalDate dueDate;
}

