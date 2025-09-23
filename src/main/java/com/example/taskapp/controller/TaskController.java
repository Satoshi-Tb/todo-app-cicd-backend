package com.example.taskapp.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskapp.dto.PageResponse;
import com.example.taskapp.dto.TaskCreateReq;
import com.example.taskapp.dto.TaskResp;
import com.example.taskapp.dto.TaskUpdateReq;
import com.example.taskapp.model.Task;
import com.example.taskapp.model.TaskStatus;
import com.example.taskapp.service.PageResult;
import com.example.taskapp.service.TaskService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Validated
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResp> create(@Valid @RequestBody TaskCreateReq req) {
        Task toCreate = new Task(null, req.getTitle(), req.getDescription(), req.getStatus(),
                req.getDueDate(), null, null, null);
        Task created = taskService.create(toCreate);
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResp.from(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResp> get(@PathVariable("id") Long id) {
        Task t = taskService.get(id);
        return ResponseEntity.ok(TaskResp.from(t));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResp> update(
            @PathVariable("id") Long id,
            @RequestHeader(name = "If-Match", required = true) long ifMatch,
            @Valid @RequestBody TaskUpdateReq req) {

        Task toUpdate = new Task(id, req.getTitle(), req.getDescription(), req.getStatus(),
                req.getDueDate(), null, null, null);

        Task updated = taskService.update(id, ifMatch, toUpdate);
        return ResponseEntity.ok()
                // ETagは数値・非引用を厳守（SpringのeTag()は引用付与するため使用しない）
                .header(HttpHeaders.ETAG, String.valueOf(updated.getVersion()))
                .body(TaskResp.from(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<PageResponse<TaskResp>> search(
            @RequestParam(name = "status", required = false) TaskStatus status,
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size) {

        PageResult<Task> result = taskService.search(status, q, page, size);
        List<TaskResp> content = result.content().stream()
                .map(TaskResp::from)
                .collect(Collectors.toList());
        PageResponse<TaskResp> body = new PageResponse<>(content, result.page(), result.size(), result.total());
        return ResponseEntity.ok(body);
    }
}

