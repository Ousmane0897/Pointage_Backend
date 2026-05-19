package com.example.Pointage_Cleanic.entities.productionchimie;

import com.example.Pointage_Cleanic.Enum.StatutOf;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "production_chimie_ordres_fabrication")
public class OrdreFabrication {

    @Id
    private String id;

    @Indexed(unique = true)
    private String numero;

    private String produitNom;

    private String formulationId;
    private String formulationCode;
    private Integer formulationVersion;

    private Double quantiteCible;
    private UniteChimie uniteCible;

    private LocalDateTime dateLancementPrevue;
    private LocalDateTime dateLancementEffective;
    private LocalDateTime dateFin;

    private String operateurResponsableId;
    private String operateurResponsableNom;

    private StatutOf statut;

    @Builder.Default
    private List<HistoriqueStatutOf> historiqueStatuts = new ArrayList<>();

    @Builder.Default
    private List<ConsommationMp> consommationMp = new ArrayList<>();

    private String lotGenereId;
    private String lotGenereNumero;
    private String commentaire;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}