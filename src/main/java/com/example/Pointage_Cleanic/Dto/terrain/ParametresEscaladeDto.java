package com.example.Pointage_Cleanic.Dto.terrain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParametresEscaladeDto {

    private String id;

    private int delaiRetardMinutes;
    private int delaiAbsenceMinutes;
    private int delaiEscaladeNiveau1Minutes;
    private int delaiEscaladeNiveau2Minutes;

    private List<String> superviseursIds;
    private List<String> responsablesOperationnelsIds;
    private List<String> directionGeneraleIds;

    private LocalDateTime updatedAt;
    private String updatedBy;
}