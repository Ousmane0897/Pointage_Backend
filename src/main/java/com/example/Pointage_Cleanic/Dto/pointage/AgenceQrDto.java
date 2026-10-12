package com.example.Pointage_Cleanic.Dto.pointage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Ligne de l'écran « QR codes des agences ». Porte le jeton (via {@code payload} et
 * {@code url}) : réservée aux profils RH / SUPERADMIN.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgenceQrDto {
    private String siteId;
    private String code;
    private String nom;
    private boolean actif;

    /** Faux ⇒ tout scan de ce QR sera refusé (« agence non géolocalisée »). */
    private boolean geolocalise;
    private Double latitude;
    private Double longitude;
    /** Rayon effectif (défaut appliqué si absent en base). */
    private Integer rayonToleranceM;

    /** {@code POINTIC:AG:<token>} — ce que le serveur valide au scan. */
    private String payload;
    /** Contenu effectivement imprimé dans le QR (URL du front portant le payload). */
    private String url;
    private Integer qrVersion;
    private LocalDateTime qrGenereLe;
}
