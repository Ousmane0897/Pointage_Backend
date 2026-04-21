package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.Enum.RegimeIpres;
import com.example.Pointage_Cleanic.entities.IndemniteCategorie;
import com.example.Pointage_Cleanic.entities.PrimeCategorie;
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

    private RegimeIpres regimeIpres;
    private Double tauxAtMp;

    private boolean actif;

    private Instant dateCreation;
    private Instant dateModification;
}