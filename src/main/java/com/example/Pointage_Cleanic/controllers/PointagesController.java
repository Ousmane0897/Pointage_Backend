package com.example.Pointage_Cleanic.controllers;

import org.springframework.security.access.prepost.PreAuthorize;
import com.example.Pointage_Cleanic.security.DroitsExpr;



import com.example.Pointage_Cleanic.Dto.pointage.PointageConfirmationDto;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.services.pointage.PointageLimiteurDebit;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import com.example.Pointage_Cleanic.models.PointageRequest;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.services.PointageServices;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pointages")
@RequiredArgsConstructor
public class PointagesController {

    private final PointageServices pointageServices;
    private final PointageRepository pointageRepository;
    private final PointageLimiteurDebit limiteurDebit;

    /**
     * Pointage public du kiosque (sans jeton). Limité en débit par IP et par appareil
     * ({@link PointageLimiteurDebit}) ; la réponse ne contient que ce que l'écran final affiche.
     */
    @PostMapping
    public ResponseEntity<?> pointer(@Valid @RequestBody PointageRequest request,
                                     HttpServletRequest httpRequest) {

        // codeSecret et deviceId sont garantis non-blank par @Valid (sinon 400 VALIDATION_ERROR).
        String cleanCode = request.getCodeSecret()
                .replaceAll("[^0-9]", ""); // 🔥 SUPPRIME TOUT SAUF CHIFFRES

        String ip = httpRequest.getRemoteAddr();
        Optional<String> refus = limiteurDebit.verifier(ip, request.getDeviceId());
        if (refus.isPresent()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(refus.get());
        }

        if (!pointageServices.canPoint(request.getDeviceId(), 20)) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Ce téléphone a déjà été utilisé pour un pointage récemment.");
        }

        // Employé introuvable / code invalide → ResourceNotFoundException (404 JSON) géré
        // par GlobalExceptionHandler ; pas de try/catch local.
        Pointage pointage;
        try {
            pointage = pointageServices.enregistrerPointage(
                    cleanCode,
                    request.getDeviceId(),
                    request.getLatitude(),
                    request.getLongitude()
            );
        } catch (ResourceNotFoundException codeInconnu) {
            limiteurDebit.signalerCodeInconnu(ip, request.getDeviceId());
            throw codeInconnu;
        }
        limiteurDebit.signalerSucces(ip, request.getDeviceId());
        return ResponseEntity.ok(PointageConfirmationDto.de(pointage));
    }

    @PreAuthorize(DroitsExpr.POINTAGES_SUPERVISION)
    @GetMapping
    public ResponseEntity<List<Pointage>> getAll() {
        List<Pointage> All= pointageRepository.findAll();
        return ResponseEntity.status(HttpStatus.CREATED).body(All);
    }

    @PreAuthorize(DroitsExpr.POINTAGES_SUPERVISION)
    @GetMapping("/today")
    public ResponseEntity<Page<Pointage>> getTodayPointages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LocalDate today = LocalDate.now();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Pointage> pointages = pointageRepository.findByDate(today, pageable);

        return ResponseEntity.ok(pointages);
    }

    // Affiche l'historique dans la possibilité de recherche et de filtrage par période dans l'historique des pointages.
    @PreAuthorize(DroitsExpr.POINTAGES_SUPERVISION)
    @GetMapping("/historique/search")
    public ResponseEntity<Page<Pointage>> searchHistorique(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
                pointageServices.searchHistorique(
                        search,
                        dateDebut,
                        dateFin,
                        page,
                        size
                )
        );
    }

    @PreAuthorize(DroitsExpr.POINTAGES_SUPERVISION)
    @GetMapping("/historique/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin
    ) throws Exception {

        byte[] file = pointageServices.exportExcel(search, dateDebut, dateFin);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=pointages.xlsx") // indique au navigateur que la réponse contient un fichier à télécharger et lui donne un nom (pointages.xlsx).
                .body(file);
    }

    @PreAuthorize(DroitsExpr.POINTAGES_SUPERVISION)
    @GetMapping("/historique/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin
    ) throws Exception {

        byte[] file = pointageServices.exportPdf(search, dateDebut, dateFin);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=pointages.pdf") // indique au navigateur que la réponse contient un fichier à télécharger et lui donne un nom (pointages.pdf).
                .body(file);
    }


}
