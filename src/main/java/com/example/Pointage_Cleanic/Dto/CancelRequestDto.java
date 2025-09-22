package com.example.Pointage_Cleanic.Dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelRequestDto {

    private String id;
    private String motif;
    private String requestedBy; // Si l'utilisateur connecté, son username est envoyé depuis angular
}
