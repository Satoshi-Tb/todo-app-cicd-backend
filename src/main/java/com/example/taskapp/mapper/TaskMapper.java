package com.example.taskapp.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.taskapp.model.Task;
import com.example.taskapp.model.TaskStatus;

@Mapper
public interface TaskMapper {

    int insert(Task task);

    Task findById(@Param("id") Long id);

    List<Task> search(
            @Param("status") TaskStatus status,
            @Param("q") String q,
            @Param("offset") int offset,
            @Param("size") int size);

    long count(
            @Param("status") TaskStatus status,
            @Param("q") String q);

    int updateWithOptimisticLock(Task task);

    int deleteById(@Param("id") Long id);
}

