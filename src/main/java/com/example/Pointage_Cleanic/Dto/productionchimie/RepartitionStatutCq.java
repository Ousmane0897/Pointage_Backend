package com.example.Pointage_Cleanic.Dto.productionchimie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepartitionStatutCq {
    private long valides;
    private long rejetes;
    private long enAttente;
}
