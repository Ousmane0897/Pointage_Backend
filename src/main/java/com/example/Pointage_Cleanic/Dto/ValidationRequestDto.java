package com.example.Pointage_Cleanic.Dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValidationRequestDto {
    private String id;
    private boolean accepted;
    private String validatedBy; // optionnel si Principal

    // getters / setters
}
