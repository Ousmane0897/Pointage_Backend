package com.example.Pointage_Cleanic.Dto.stockv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Message WebSocket de validation stock.
 * type : BON_SOUMIS | BON_VALIDE | BON_REFUSE | BON_EFFECTIF | INFO
 * sens : ENTREE | SORTIE
 * dateEmission : ISO complet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationStockDto {
    private String type;
    private String sens;
    private String bonId;
    private String reference;
    private String titre;
    private String message;
    private String dateEmission;
}
