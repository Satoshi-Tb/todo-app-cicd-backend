package com.example.taskapp.dto;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long total) {}

