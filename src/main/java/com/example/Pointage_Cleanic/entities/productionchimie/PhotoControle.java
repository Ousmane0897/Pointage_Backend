package com.example.Pointage_Cleanic.entities.productionchimie;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoControle {

    private String nomFichier;
    private String mimeType;
    private Long taille;

    @JsonIgnore
    private byte[] contenu;
}
