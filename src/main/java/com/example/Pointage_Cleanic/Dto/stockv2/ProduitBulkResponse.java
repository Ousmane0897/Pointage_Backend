package com.example.Pointage_Cleanic.Dto.stockv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse d'un import transactionnel /bulk. Même corps en 200 (succès total) et 422 (échec).
 * Le front mappe inserted -> succès, errors[].numeroLigne/code/message -> échecs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProduitBulkResponse {

    private int total;
    private int inserted;
    private int failed;
    private List<String> insertedIds;
    private List<BulkError> errors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BulkError {
        private Integer numeroLigne;
        private Integer lineNumber;
        private String code;
        private String message;
        private String field;
    }
}
