package com.example.Pointage_Cleanic.entities;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrancheIr {

    private Long borneMin;
    private Long borneMax;
    private Double taux;
}