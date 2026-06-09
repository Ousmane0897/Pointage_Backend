package com.example.Pointage_Cleanic.controllers.rh.gestionpersonnel;

import com.example.Pointage_Cleanic.Dto.rh.AlertePeriodeEssaiDossierDto;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeBulkImportRequest;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeBulkImportResponse;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeDto;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeStatutRequest;
import com.example.Pointage_Cleanic.Enum.StrategieErreursImport;
import com.example.Pointage_Cleanic.services.rh.DossierEmployeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/gestion-personnel/employes")
@RequiredArgsConstructor
public class DossierEmployeController {

    private final DossierEmployeService service;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<Page<DossierEmployeDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) String site,
            @RequestParam(required = false) String poste,
            @RequestParam(required = false) String statut
    ) {
        return ResponseEntity.ok(service.list(page, size, q, departement, site, poste, statut));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DossierEmployeDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DossierEmployeDto> create(
            @RequestPart("dossier") String dossierJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) throws IOException {
        DossierEmployeDto dto = objectMapper.readValue(dossierJson, DossierEmployeDto.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto, photo));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DossierEmployeDto> update(
            @PathVariable String id,
            @RequestPart("employe") String dossierJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) throws IOException {
        DossierEmployeDto dto = objectMapper.readValue(dossierJson, DossierEmployeDto.class);
        return ResponseEntity.ok(service.update(id, dto, photo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/statut")
    public ResponseEntity<DossierEmployeDto> updateStatut(
            @PathVariable String id,
            @RequestBody DossierEmployeStatutRequest request
    ) {
        return ResponseEntity.ok(service.updateStatut(id, request));
    }

    @PutMapping("/{id}/titulariser")
    public ResponseEntity<DossierEmployeDto> titulariser(@PathVariable String id) {
        return ResponseEntity.ok(service.titulariser(id));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> getPhoto(@PathVariable String id) {
        byte[] photo = service.getPhoto(id);
        if (photo == null || photo.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(photo);
    }

    @GetMapping("/alertes-essai")
    public ResponseEntity<List<AlertePeriodeEssaiDossierDto>> getAlertesEssai() {
        return ResponseEntity.ok(service.getAlertesPeriodeEssai());
    }

    /**
     * Import massif de dossiers employés. Consommé par le frontend à la place
     * d'une boucle d'appels POST /api/gestion-personnel/employes pour les imports Excel.
     * <p>
     * Le champ {@code strategieErreurs} est optionnel : s'il est absent du payload,
     * la stratégie {@code TOUT_OU_RIEN} est appliquée par défaut.
     * <p>
     * Codes HTTP :
     * <ul>
     *   <li>200 : toutes les lignes insérées.</li>
     *   <li>207 : import partiel (stratégie IMPORTER_LIGNES_VALIDES avec erreurs).</li>
     *   <li>400 : payload invalide (vide, trop grand).</li>
     *   <li>422 : stratégie TOUT_OU_RIEN avec au moins une erreur → aucune insertion.</li>
     * </ul>
     */
    @PostMapping(value = "/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DossierEmployeBulkImportResponse> importBulk(
            @RequestBody DossierEmployeBulkImportRequest request
    ) {
        String userEmail = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) userEmail = auth.getName();

        DossierEmployeBulkImportResponse response = service.importBulk(request, userEmail);

        if (response.errors().isEmpty()) {
            return ResponseEntity.ok(response);
        }
        if (request.strategieErreurs() == StrategieErreursImport.TOUT_OU_RIEN) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }
        return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
    }
}