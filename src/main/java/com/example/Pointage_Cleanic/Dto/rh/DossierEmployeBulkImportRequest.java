package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.StrategieErreursImport;

import java.util.List;

public record DossierEmployeBulkImportRequest(
        List<DossierEmployeBulkLigneDto> employes,
        StrategieErreursImport strategieErreurs
) {
    public DossierEmployeBulkImportRequest {
        if (strategieErreurs == null) {
            strategieErreurs = StrategieErreursImport.TOUT_OU_RIEN;
        }
    }
}