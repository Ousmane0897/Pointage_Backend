package com.example.Pointage_Cleanic.Dto;


import com.example.Pointage_Cleanic.entities.EmployeComplet;
import lombok.Data;

import java.util.Optional;

@Data
public class MouvementStock {

    private String codeProduit;
    private String nomProduit;
    private Integer quantite;
}
