package com.example.Pointage_Cleanic.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avenant {

    private String id;
    private String contratId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateCreation;

    private String objet;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEffet;
}