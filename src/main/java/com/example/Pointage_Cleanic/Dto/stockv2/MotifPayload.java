package com.example.Pointage_Cleanic.Dto.stockv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Corps des suppressions définitives : motif obligatoire, journalisé.
 * <p>
 * Volontairement distinct de {@link DecisionPayload} (dont le champ s'appelle {@code commentaire}
 * et reste optionnel) : ici le motif est requis et le contrat doit le dire.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MotifPayload {
    private String motif;
}
