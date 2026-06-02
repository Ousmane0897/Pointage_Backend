package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.NiveauEscalade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Une étape de l'historique d'escalade d'une alerte. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscaladeEntry {
    private NiveauEscalade niveau;
    private String destinataireId;
    private String destinataireNom;
    private LocalDateTime dateEscalade;
    private String motif;
}