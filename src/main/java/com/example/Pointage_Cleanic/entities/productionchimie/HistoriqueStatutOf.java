package com.example.Pointage_Cleanic.entities.productionchimie;

import com.example.Pointage_Cleanic.Enum.StatutOf;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoriqueStatutOf {
    private StatutOf statutPrecedent;
    private StatutOf statutNouveau;
    private LocalDateTime date;
    private String auteurId;
    private String auteurNom;
    private String commentaire;
}