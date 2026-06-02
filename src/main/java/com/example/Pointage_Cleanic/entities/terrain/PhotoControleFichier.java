package com.example.Pointage_Cleanic.entities.terrain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Photo d'un contrôle qualité terrain (byte[] inline, exclu du JSON). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoControleFichier {
    private String nomFichier;
    private String mimeType;
    private String legende;

    @JsonIgnore
    private byte[] contenu;
}