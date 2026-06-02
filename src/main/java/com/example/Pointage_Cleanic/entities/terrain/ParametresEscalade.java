package com.example.Pointage_Cleanic.entities.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Paramètres d'escalade des alertes (document singleton). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "terrain_parametres_escalade")
public class ParametresEscalade {

    @Id
    private String id;

    private int delaiRetardMinutes;
    private int delaiAbsenceMinutes;
    private int delaiEscaladeNiveau1Minutes;
    private int delaiEscaladeNiveau2Minutes;

    @Builder.Default
    private List<String> superviseursIds = new ArrayList<>();
    @Builder.Default
    private List<String> responsablesOperationnelsIds = new ArrayList<>();
    @Builder.Default
    private List<String> directionGeneraleIds = new ArrayList<>();

    private LocalDateTime updatedAt;
    private String updatedBy;
}