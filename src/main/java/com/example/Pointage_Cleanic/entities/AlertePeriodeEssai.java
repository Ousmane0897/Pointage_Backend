package com.example.Pointage_Cleanic.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertePeriodeEssai {

    private String id;
    private String periodeEssaiId;
    private int joursAvant;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateAlerte;

    private boolean envoyee;
}