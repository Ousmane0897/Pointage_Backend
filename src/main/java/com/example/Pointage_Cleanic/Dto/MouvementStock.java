package com.example.Pointage_Cleanic.Dto;


import lombok.Data;

@Data
public class MouvementStock {

    private String codeProduit;
    private String nomProduit;
    private Integer quantite;
}
