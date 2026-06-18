package com.example.Pointage_Cleanic.Dto.stockv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Consommation agrégée par destinataire (site / agent / client). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsommationDestinataireDto {
    private String destinataireId;
    private String destinataireNom;
    private String typeDestinataire;
    private double quantiteTotale;
    private long montantTotal;
    private long nbSorties;
    private List<EvolutionConsommationDto> evolution;
    private List<LigneConsommationDto> lignes;
}
