package com.example.Pointage_Cleanic.Dto;


import java.util.List;


public record ImportEmployeResponse(
        List<EmployeCompletDto> success,
        List<ImportError> errors
) {}

