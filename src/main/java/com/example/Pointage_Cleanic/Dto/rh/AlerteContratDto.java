package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.TypeContratRh;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlerteContratDto {

    private String contratId;
    private String employeId;
    private String employeNom;
    private String employePrenom;
    private TypeContratRh typeContrat;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private long joursRestants;
}