package com.example.Pointage_Cleanic.entities.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contact d'un site client. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactClient {
    private String nom;
    private String fonction;
    private String telephone;
    private String email;
}