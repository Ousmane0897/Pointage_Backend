package com.example.Pointage_Cleanic.Dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnnulationDecisionMessage {
    private String planificationId;
    private boolean accepted;
    private String validatedBy;
    private String motif;
    private String dateDecision;
    // getters / setters
}
