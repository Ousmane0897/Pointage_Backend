package com.example.Pointage_Cleanic.entities.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Signature client d'une fiche d'intervention. {@code dataUrl} = PNG base64. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatureClient {
    private String nom;
    private String fonction;
    private String dataUrl;
    private String date;
}