package com.example.Pointage_Cleanic.Dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnnulationRequestMessage {
    private String planificationId;
    private String prenomNom;
    private String motif;
    private String requestedBy;
    private String dateRequest;
    // getters / setters
}
