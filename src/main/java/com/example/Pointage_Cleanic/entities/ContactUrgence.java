package com.example.Pointage_Cleanic.entities;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactUrgence {

    private String nom;
    private String lienParente;
    private String telephone;
}