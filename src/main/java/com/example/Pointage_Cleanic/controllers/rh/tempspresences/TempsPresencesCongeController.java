package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.rh.CompteursAValiderDto;
import com.example.Pointage_Cleanic.Dto.rh.DemandeCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.EmployeSelectionnableDto;
import com.example.Pointage_Cleanic.Dto.rh.MonProfilCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.SoldeCongeDto;
import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.services.rh.CongeIdentiteService;
import com.example.Pointage_Cleanic.services.rh.CongeWorkflowService;
import com.example.Pointage_Cleanic.services.rh.DemandeCongeService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Façade RH 6.2 « Temps & Présences » — soldes, demandes de congés et circuit de validation
 * à 3 niveaux ({@code supérieur hiérarchique → RH → Direction générale}).
 *
 * <p>Aucune décision ne prend d'identifiant de décideur du client : l'appelant est résolu
 * depuis le JWT, et le <b>niveau est déduit serveur du statut courant</b>
 * ({@link CongeWorkflowService}). Les tentatives non habilitées reçoivent un 403, les
 * transitions sur une demande déjà traitée un 409.
 */
@RestController
@RequestMapping("/api/temps-presences/conges")
@RequiredArgsConstructor
public class TempsPresencesCongeController {

    private final DemandeCongeService demandeCongeService;
    private final CongeWorkflowService congeWorkflowService;
    private final CongeIdentiteService congeIdentiteService;

    // --- Identité de l'appelant ---

    /**
     * Profil métier de l'utilisateur connecté (le JWT ne porte ni id ni employeId).
     * {@code employeId} est null si le compte n'est rattaché à aucun dossier employé.
     */
    @GetMapping("/moi")
    public ResponseEntity<MonProfilCongeDto> monProfil() {
        return ResponseEntity.ok(congeWorkflowService.monProfil());
    }

    /**
     * Employés au nom desquels l'appelant peut déposer une demande — alimente le champ
     * « Employé » du formulaire de demande.
     *
     * <p>Tous les employés pour {@code RH} / {@code SUPERADMIN}, lui-même et ses subordonnés
     * directs pour {@code EXPLOITATION}, lui-même seul pour tout autre profil. Un compte non
     * rattaché à un dossier employé reçoit une liste <b>vide</b>, jamais totale.
     *
     * <p>⚠ Volontairement sous {@code /conges/} et non sous {@code /gestion-personnel/} : un
     * compte {@code EXPLOITATION} n'a pas le droit de lecture du référentiel employés. Non
     * paginé — la liste est bornée par construction.
     */
    @GetMapping("/employes-selectionnables")
    public ResponseEntity<List<EmployeSelectionnableDto>> employesSelectionnables() {
        return ResponseEntity.ok(demandeCongeService.getEmployesSelectionnables());
    }

    // --- Soldes (tableau brut / objet seul) ---

    @GetMapping("/soldes")
    public ResponseEntity<List<SoldeCongeDto>> getSoldes(
            @RequestParam(required = false) String employeId) {
        if (employeId != null && !employeId.isBlank()) {
            return ResponseEntity.ok(List.of(demandeCongeService.getSolde(employeId)));
        }
        return ResponseEntity.ok(demandeCongeService.getSoldes());
    }

    @GetMapping("/soldes/{employeId}")
    public ResponseEntity<SoldeCongeDto> getSolde(@PathVariable String employeId) {
        return ResponseEntity.ok(demandeCongeService.getSolde(employeId));
    }

    /** Solde de l'appelant — évite au front de connaître son propre employeId. */
    @GetMapping("/soldes/moi")
    public ResponseEntity<SoldeCongeDto> monSolde() {
        String employeId = congeIdentiteService.employeIdCourant();
        if (employeId == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(demandeCongeService.getSolde(employeId));
    }

    // --- Demandes (CRUD paginé) ---

    @GetMapping("/demandes")
    public ResponseEntity<PageResponse<DemandeCongeDto>> searchDemandes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) List<String> statut,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String niveau
    ) {
        return ResponseEntity.ok(PageResponse.from(demandeCongeService.searchDemandes(
                employeId, departement, statut, type, dateDebut, dateFin, q, niveau, page, size)));
    }

    /** Demandes de l'appelant (demandeur déduit du JWT). */
    @GetMapping("/demandes/mes-demandes")
    public ResponseEntity<PageResponse<DemandeCongeDto>> mesDemandes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(PageResponse.from(congeWorkflowService.mesDemandes(page, size)));
    }

    /**
     * File de validation : uniquement ce que l'appelant peut trancher maintenant
     * (subordonnés directs au niveau 1, file RH, file Direction).
     */
    @GetMapping("/demandes/a-valider")
    public ResponseEntity<PageResponse<DemandeCongeDto>> demandesAValider(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) NiveauValidationConge niveau
    ) {
        return ResponseEntity.ok(PageResponse.from(
                congeWorkflowService.demandesAValider(niveau, page, size)));
    }

    /** Compteurs par niveau pour les onglets de la file de validation. */
    @GetMapping("/demandes/a-valider/compteurs")
    public ResponseEntity<CompteursAValiderDto> compteursAValider() {
        return ResponseEntity.ok(congeWorkflowService.compteurs());
    }

    @GetMapping("/demandes/{id}")
    public ResponseEntity<DemandeCongeDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(congeWorkflowService.getPourAppelant(id));
    }

    @GetMapping("/demandes/employe/{employeId}")
    public ResponseEntity<List<DemandeCongeDto>> getByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(demandeCongeService.getByEmployeId(employeId));
    }

    @PostMapping("/demandes")
    public ResponseEntity<DemandeCongeDto> create(@RequestBody DemandeCongeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(demandeCongeService.create(dto));
    }

    @PutMapping("/demandes/{id}")
    public ResponseEntity<DemandeCongeDto> update(@PathVariable String id, @RequestBody DemandeCongeDto dto) {
        return ResponseEntity.ok(demandeCongeService.update(id, dto));
    }

    @DeleteMapping("/demandes/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        demandeCongeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Transitions du circuit ---

    /** Fait avancer la demande d'un cran ; le niveau est déduit du statut courant. */
    @PostMapping("/demandes/{id}/valider")
    public ResponseEntity<DemandeCongeDto> valider(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String commentaire = body != null ? body.get("commentaire") : null;
        return ResponseEntity.ok(demandeCongeService.valider(id, commentaire));
    }

    /**
     * @deprecated alias de {@link #valider}, conservé le temps de la bascule du frontend.
     */
    @Deprecated
    @PostMapping("/demandes/{id}/approuver")
    public ResponseEntity<DemandeCongeDto> approuver(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String commentaire = body != null ? body.get("commentaire") : null;
        return ResponseEntity.ok(demandeCongeService.valider(id, commentaire));
    }

    /** Refus terminal — le motif est obligatoire (422 s'il est absent ou trop court). */
    @PostMapping("/demandes/{id}/refuser")
    public ResponseEntity<DemandeCongeDto> refuser(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String motif = body != null
                ? (body.get("motif") != null ? body.get("motif") : body.get("commentaire"))
                : null;
        return ResponseEntity.ok(demandeCongeService.refuser(id, motif));
    }
}
