package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValiderBulletinRequest {

    private String validateurId;
    private String validateurNom;
    private String commentaire;
}