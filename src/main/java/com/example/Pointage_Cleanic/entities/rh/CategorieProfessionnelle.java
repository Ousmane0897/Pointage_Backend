package com.example.Pointage_Cleanic.entities.rh;

import com.example.Pointage_Cleanic.Enum.rh.RegimeIpres;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "categories_professionnelles")
public class CategorieProfessionnelle {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String libelle;
    private String description;

    private Long salaireBase;

    @Builder.Default
    private List<PrimeCategorie> primes = new ArrayList<>();

    @Builder.Default
    private List<IndemniteCategorie> indemnites = new ArrayList<>();

    @Builder.Default
    private List<PretCategorie> prets = new ArrayList<>();

    @Builder.Default
    private List<AvanceCategorie> avancesSurSalaire = new ArrayList<>();

    @Builder.Default
    private List<RetenueCategorie> retenues = new ArrayList<>();

    private RegimeIpres regimeIpres;

    private Double tauxAtMp;

    private boolean actif;

    private Instant dateCreation;
    private Instant dateModification;
}