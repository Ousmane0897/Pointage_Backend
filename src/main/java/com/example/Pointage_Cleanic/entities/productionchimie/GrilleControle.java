package com.example.Pointage_Cleanic.entities.productionchimie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "production_chimie_grilles_controle")
public class GrilleControle {

    @Id
    private String id;

    private String produitNom;
    private String formulationId;

    @Builder.Default
    private List<TestControle> tests = new ArrayList<>();

    private boolean actif;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
