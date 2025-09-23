package com.example.taskapp.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskapp.exception.NotFoundException;
import com.example.taskapp.exception.OptimisticLockException;
import com.example.taskapp.mapper.TaskMapper;
import com.example.taskapp.model.Task;
import com.example.taskapp.model.TaskStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    private final TaskMapper taskMapper;

    @Transactional
    public Task create(Task task) {
        if (task == null) throw new IllegalArgumentException("task must not be null");
        if (task.getVersion() == null) task.setVersion(0L);
        Instant now = Instant.now();
        if (task.getCreatedAt() == null) task.setCreatedAt(now);
        if (task.getUpdatedAt() == null) task.setUpdatedAt(now);

        taskMapper.insert(task);
        return taskMapper.findById(task.getId());
    }

    public Task get(Long id) {
        Task t = taskMapper.findById(id);
        if (t == null) throw new NotFoundException("Task not found: " + id);
        return t;
    }

    @Transactional
    public Task update(Long id, long ifMatchVersion, Task task) {
        if (task == null) throw new IllegalArgumentException("task must not be null");

        Task toUpdate = new Task();
        toUpdate.setId(id);
        toUpdate.setTitle(task.getTitle());
        toUpdate.setDescription(task.getDescription());
        toUpdate.setStatus(task.getStatus());
        toUpdate.setDueDate(task.getDueDate());
        toUpdate.setVersion(ifMatchVersion);

        int updated = taskMapper.updateWithOptimisticLock(toUpdate);
        if (updated == 0) {
            Task existing = taskMapper.findById(id);
            if (existing == null) {
                throw new NotFoundException("Task not found: " + id);
            }
            throw new OptimisticLockException(
                    "Version conflict. expected=" + ifMatchVersion + ", actual=" + existing.getVersion());
        }
        return taskMapper.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        int deleted = taskMapper.deleteById(id);
        if (deleted == 0) throw new NotFoundException("Task not found: " + id);
    }

    public PageResult<Task> search(TaskStatus status, String q, int page, int size) {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
        int offset = page * size;
        List<Task> content = taskMapper.search(status, q, offset, size);
        long total = taskMapper.count(status, q);
        return new PageResult<>(content, page, size, total);
    }
}

