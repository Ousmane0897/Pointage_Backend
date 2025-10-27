package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.Enum.MotifMouvementSortieStock;
import com.example.Pointage_Cleanic.Enum.TypeMouvement;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class SortieBatchRequest {
    private List<MouvementStock> mouvements; //MouvementStock représente un produit avec son codeProduit, nomProduit et quantite
    private TypeMouvement typeMouvement;
    private String destination;
    private MotifMouvementSortieStock motifSortieStock;
    private String responsable;

    //@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    //private Instant dateSortie;
}