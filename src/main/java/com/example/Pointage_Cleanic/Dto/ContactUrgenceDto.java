package com.example.Pointage_Cleanic.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactUrgenceDto {

    private String nom;
    private String lienParente;
    private String telephone;
}