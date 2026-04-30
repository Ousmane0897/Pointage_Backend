package com.example.Pointage_Cleanic.Dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganigrammeNodeDto {

    private String id;
    private String agentId;
    private String nomComplet;
    private String poste;
    private String agence;
    private String managerOps;
    private String chefEquipe;
    private String statut;
    private byte[] photo;
}