package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.Enum.DecisionDocument;

public record ValiderDocumentRequest(DecisionDocument statut, String commentaire) {
}
