package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.MomentPhoto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Photo d'intervention stockée inline (byte[] exclu du JSON). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoInterventionFichier {

    private String nomFichier;
    private String mimeType;
    private Long tailleOctets;
    private MomentPhoto moment;
    private String legende;

    @JsonIgnore
    private byte[] contenu;
}