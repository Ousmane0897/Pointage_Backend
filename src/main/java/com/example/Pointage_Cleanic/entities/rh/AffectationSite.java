package com.example.Pointage_Cleanic.entities.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

/**
 * Affectation d'un employé à un site : tranche horaire, période de présence et
 * semaine ouvrée — ces trois informations étant <b>propres au site</b> (un agent
 * multi-sites peut y être arrivé à des dates différentes et n'y pas travailler les
 * mêmes jours). Sous-document embarqué dans {@link DossierEmploye} (calqué sur
 * {@link ContactUrgence}).
 * <p>
 * Les horaires sont stockés au format {@code "HH:mm"} (ex. « 06:00 »). Ils sont
 * facultatifs : {@code horaireDebut} et {@code horaireFin} peuvent être null.
 * Lorsqu'ils sont tous deux présents, {@code horaireDebut} doit être
 * strictement antérieur à {@code horaireFin} (validé côté service).
 * <p>
 * <b>Période et semaine ouvrée.</b> {@code dateEntree} est l'arrivée de l'employé
 * <i>sur ce site</i> — à ne pas confondre avec {@code DossierEmploye.dateEmbauche},
 * son entrée dans l'entreprise. {@code dateSortie} absente signifie « toujours en
 * poste sur ce site ». Les deux bornes sont facultatives : l'import bulk et le repli
 * {@link com.example.Pointage_Cleanic.util.SiteAffecteUtils#affectationsDepuisSiteAffecte}
 * produisent des affectations sans aucune date, et les refuser casserait ces flux.
 * <p>
 * {@code joursTravail} est porté en {@code String} (nullable), comme le champ
 * homonyme de {@link DossierEmploye}, et validé contre l'enum
 * {@link com.example.Pointage_Cleanic.Enum.rh.JoursTravail} côté service. Il est nul
 * sur les dossiers antérieurs ; les consommateurs appliquent alors l'échelle de
 * replis « par site → par employé → aucun filtrage » (voir
 * {@link com.example.Pointage_Cleanic.services.rh.PlanningAffectationResolver}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AffectationSite {

    /**
     * Identité stable de la ligne, posée par
     * {@link com.example.Pointage_Cleanic.util.AffectationSiteUtils#assurerIds}.
     * <p>
     * Sous-document embarqué : pas de {@code @Id}, l'identité Mongo reste celle du
     * dossier. Ce champ existe parce que la liste est <b>remplacée en bloc</b> à
     * chaque écriture — sans lui, aucune ligne ne survit à un enregistrement en tant
     * qu'objet identifiable. Nul sur les dossiers antérieurs au backfill.
     * <p>
     * ⚠ Ne PAS s'en servir pour rapprocher l'ancienne et la nouvelle liste dans
     * {@code DossierEmployeService} : un client qui ne le renverrait pas contournerait
     * la garde. Le rapprochement se fait sur la clé naturelle ({@link
     * com.example.Pointage_Cleanic.util.AffectationSiteUtils#signature}).
     */
    private String id;

    private String site;
    private String horaireDebut;
    private String horaireFin;

    /** Arrivée de l'employé SUR CE SITE (≠ {@code DossierEmploye.dateEmbauche}). */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEntree;

    /** Départ de ce site. Null ⇒ l'employé y est toujours en poste. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateSortie;

    /** Semaine ouvrée PROPRE À CE SITE : LUN_VEN, LUN_SAM ou LUN_DIM. Nullable. */
    private String joursTravail;
}
