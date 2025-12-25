package com.example.Pointage_Cleanic.Mapper;

import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DateMapper {

    public LocalDate asLocalDate(String value) {
        if (value == null || value.isBlank()) return null;
        return LocalDate.parse(value);
    }
}
