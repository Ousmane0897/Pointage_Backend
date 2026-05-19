package com.example.Pointage_Cleanic.util;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> content, long totalElements) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements());
    }

    public static <T> PageResponse<T> of(List<T> content, long totalElements) {
        return new PageResponse<>(content, totalElements);
    }
}