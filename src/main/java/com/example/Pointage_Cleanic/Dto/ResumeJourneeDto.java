package com.example.Pointage_Cleanic.Dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeJourneeDto {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private int totalEmployes;
    private int presents;
    private int absents;
    private int retards;
    private int enConge;
}