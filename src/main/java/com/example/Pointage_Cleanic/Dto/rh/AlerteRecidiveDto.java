package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.TypeSanction;
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
public class AlerteRecidiveDto {

    private String employeId;
    private String nom;
    private String prenom;
    private Integer nombreSanctions;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate derniereDate;

    private TypeSanction derniereType;
    private String message;
}