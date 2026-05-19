package com.example.Pointage_Cleanic.entities.productionchimie;

import com.example.Pointage_Cleanic.Enum.TypeContenant;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "production_chimie_formats_conditionnement")
public class FormatConditionnement {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String libelle;
    private Double volume;
    private UniteChimie uniteVolume;
    private TypeContenant typeContenant;
    private DimensionsContenant dimensions;
    private Double poidsVide;
    private boolean actif;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}