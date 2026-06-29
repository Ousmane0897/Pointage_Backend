package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Destinataire d'un bon de sortie. Selon {@code type} :
 * SITE -> siteId requis ; AGENT -> agentId requis ; CLIENT -> clientNom requis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DestinataireBon {
    private TypeDestinataire type;
    private String siteId;
    private String siteNom;
    private String agentId;
    private String agentNom;
    private String clientNom;
}
