package com.example.Pointage_Cleanic.Dto.stockv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class CategorieStockDto {

    private String id;
    private String libelle;
    private String parentId;
    private int niveau;
    private Integer nbEnfants;
    private Integer nbProduits;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
