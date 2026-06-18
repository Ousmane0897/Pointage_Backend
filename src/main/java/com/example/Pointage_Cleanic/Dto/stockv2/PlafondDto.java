package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.GranularitePlafond;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlafondDto {
    private String id;
    private String siteId;
    private String siteNom;
    private GranularitePlafond granularite;
    private String cibleId;
    private String cibleLibelle;
    private UniteStock unite;
    private long plafondMensuel;
    private boolean actif;
    private String commentaire;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
