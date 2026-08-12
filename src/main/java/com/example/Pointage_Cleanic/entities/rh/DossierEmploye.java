package com.example.Pointage_Cleanic.entities.rh;

import com.example.Pointage_Cleanic.Enum.rh.GenreEmploye;
import com.example.Pointage_Cleanic.Enum.rh.SituationMatrimoniale;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "dossiers_employes")
public class DossierEmploye {

    @Id
    private String id;

    @Indexed(unique = true)
    private String matricule;

    // Code agent à 4 chiffres = clé du pointage (le codeSecret envoyé par le
    // mobile correspond à cet agentId). Unique globalement, sparse car des
    // dossiers historiques peuvent ne pas encore le porter.
    @Indexed(unique = true, sparse = true)
    private String agentId;

    // Identité
    private String nom;
    private String prenom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateNaissance;

    private GenreEmploye genre;
    private String nationalite;
    private String numeroIdentification;
    private SituationMatrimoniale situationMatrimoniale;
    private Integer nombreEnfants;

    // Poste
    private String poste;
    private String departement;
    // Chaîne « source de vérité » historique des sites, dérivée de affectations
    // (noms joints par " - "). Conservée pour la rétro-compatibilité de tous
    // les consommateurs (pointage centralisé, filtres, KPI RH).
    private String siteAffecte;
    // Affectations multi-sites avec tranche horaire optionnelle. Remplacées
    // intégralement à chaque écriture ; siteAffecte en est dérivé côté serveur.
    private List<AffectationSite> affectations;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEntree;

    private StatutDossierEmploye statut;
    private String superieurHierarchiqueId;
    private String superieurHierarchiqueNom;
    private Integer dureeEssaiMois;

    // Rythme de travail hebdomadaire : une des valeurs de l'enum JoursTravail
    // (LUN_VEN, LUN_SAM, LUN_DIM). Nullable — les dossiers historiques ne le
    // portent pas ; le défaut (LUN_VEN) est appliqué côté frontend, pas ici.
    private String joursTravail;

    // Contacts
    private String telephone;

    // Indexé (non unique) : sert de jointure entre le compte de connexion
    // — l'email est le subject du JWT — et le dossier employé, cf. CongeIdentiteService.
    @Indexed
    private String email;
    private String adresse;

    // Urgence
    private ContactUrgence contactUrgence;

    // Photo (stockée en byte[], exposée via endpoint dédié)
    private byte[] photo;

    // Données paie (non présentes dans le modèle TS DossierEmploye, mais
    // nécessaires au module 6.3 pour générer le bulletin — saisies côté
    // RH/paie, peuvent rester null en création initiale)
    private String categorieCode;
    private String numeroIpres;
    private String numeroCss;
    private String rib;
    private String banque;
}