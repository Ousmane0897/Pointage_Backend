package com.example.Pointage_Cleanic.Dto.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.TypeAlerteDelai;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlerteDelaiPhyto {
    private String applicationId;
    private TypeAlerteDelai type;
    private String siteId;
    private String siteNom;
    private String zoneTraitee;
    private String produitNom;
    private LocalDateTime dateFinContrainte;
    private long heuresRestantes;
}