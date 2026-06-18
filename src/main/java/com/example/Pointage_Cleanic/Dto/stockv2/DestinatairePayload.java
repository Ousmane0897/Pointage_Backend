package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Destinataire d'un bon de sortie. type=SITE -> siteId ; AGENT -> agentId ; CLIENT -> clientNom. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DestinatairePayload {
    private TypeDestinataire type;
    private String siteId;
    private String agentId;
    private String clientNom;
}
