package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.RegimeIpres;
import com.example.Pointage_Cleanic.entities.rh.AvanceCategorie;
import com.example.Pointage_Cleanic.entities.rh.IndemniteCategorie;
import com.example.Pointage_Cleanic.entities.rh.PretCategorie;
import com.example.Pointage_Cleanic.entities.rh.PrimeCategorie;
import com.example.Pointage_Cleanic.entities.rh.RetenueCategorie;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CategorieProfessionnelleDto {

    private String id;
    private String code;
    private String libelle;
    private String description;

    private Long salaireBase;

    private List<PrimeCategorie> primes;
    private List<IndemniteCategorie> indemnites;
    private List<PretCategorie> prets;
    private List<AvanceCategorie> avancesSurSalaire;
    private List<RetenueCategorie> retenues;

    private RegimeIpres regimeIpres;
    private Double tauxAtMp;

    private boolean actif;

    private Instant dateCreation;
    private Instant dateModification;
}