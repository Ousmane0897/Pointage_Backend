package com.example.Pointage_Cleanic.Dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganigrammeArbreDto {

    private String id;
    private String nomComplet;
    private String poste;
    private String agence;
    private List<OrganigrammeArbreDto> sousOrdres;
}