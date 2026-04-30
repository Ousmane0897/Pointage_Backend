package com.example.Pointage_Cleanic.Dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AjouterAvenantRequest {

    private String objet;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEffet;
}