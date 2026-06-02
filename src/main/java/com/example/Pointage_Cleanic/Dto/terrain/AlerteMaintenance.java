package com.example.Pointage_Cleanic.Dto.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.NiveauAlerteMaintenance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlerteMaintenance {
    private String materielId;
    private String materielCode;
    private String materielNom;
    private LocalDate prochaineMaintenance;
    private long joursRestants;
    private NiveauAlerteMaintenance niveau;
}