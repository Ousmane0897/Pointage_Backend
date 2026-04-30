package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.Enum.ActionValidation;

public record ValiderDemandeRequest(ActionValidation decision, String commentaire) {
}