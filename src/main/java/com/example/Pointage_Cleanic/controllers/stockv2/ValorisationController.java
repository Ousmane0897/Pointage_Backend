package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ChantierValoriseDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ComparatifCoutSitesDto;
import com.example.Pointage_Cleanic.Dto.stockv2.CoutProduitDto;
import com.example.Pointage_Cleanic.Dto.stockv2.CoutRevientChantierDto;
import com.example.Pointage_Cleanic.Dto.stockv2.HistoriqueCoutProduitDto;
import com.example.Pointage_Cleanic.Dto.stockv2.LigneCoutMouvementDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ParametrageValorisationDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ParametrageValorisationPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.RapportTableauBordFinancierDto;
import com.example.Pointage_Cleanic.Dto.stockv2.SyntheseMargesDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ValeurStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.example.Pointage_Cleanic.Enum.stockv2.PeriodeComparaison;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutChantier;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.services.stockv2.CoutMouvementService;
import com.example.Pointage_Cleanic.services.stockv2.CoutProduitService;
import com.example.Pointage_Cleanic.services.stockv2.CoutSiteService;
import com.example.Pointage_Cleanic.services.stockv2.MargesService;
import com.example.Pointage_Cleanic.services.stockv2.ParametrageValorisationService;
import com.example.Pointage_Cleanic.services.stockv2.TableauBordFinancierService;
import com.example.Pointage_Cleanic.services.stockv2.ValeurStockService;
import com.example.Pointage_Cleanic.services.stockv2.ValorisationChantierService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Endpoints de valorisation financière (Stock v2 7.6). Lecture seule (sauf PUT du paramétrage) ;
 * les écritures de coût se font à l'entrée de stock et via les PATCH produits dédiés.
 */
@RestController
@RequestMapping("/api/stock/valorisation")
@RequiredArgsConstructor
public class ValorisationController {

    private final ParametrageValorisationService parametrageService;
    private final CoutProduitService coutProduitService;
    private final CoutMouvementService coutMouvementService;
    private final ValeurStockService valeurStockService;
    private final CoutSiteService coutSiteService;
    private final ValorisationChantierService chantierService;
    private final MargesService margesService;
    private final TableauBordFinancierService tableauBordService;

    // ---------------------------------------------------------------- Paramétrage

    @GetMapping("/parametrage")
    public ResponseEntity<ParametrageValorisationDto> getParametrage() {
        return ResponseEntity.ok(parametrageService.get());
    }

    @PutMapping("/parametrage")
    public ResponseEntity<ParametrageValorisationDto> updateParametrage(
            @RequestBody ParametrageValorisationPayload payload) {
        return ResponseEntity.ok(parametrageService.update(payload));
    }

    // ---------------------------------------------------------------- Coût par produit

    @GetMapping("/couts-produits")
    public ResponseEntity<PageResponse<CoutProduitDto>> coutsProduits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TypeProduit typeProduit,
            @RequestParam(required = false) String categorieId,
            @RequestParam(required = false) MethodeValorisation methode,
            @RequestParam(required = false) Boolean avecAlerte) {
        return ResponseEntity.ok(
                coutProduitService.list(page, size, q, typeProduit, categorieId, methode, avecAlerte));
    }

    @GetMapping("/couts-produits/{id}/historique")
    public ResponseEntity<HistoriqueCoutProduitDto> historique(@PathVariable String id) {
        return ResponseEntity.ok(coutProduitService.historique(id));
    }

    // ---------------------------------------------------------------- Mouvements valorisés

    @GetMapping("/mouvements")
    public ResponseEntity<PageResponse<LigneCoutMouvementDto>> mouvements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String produitId,
            @RequestParam(required = false) TypeMouvement type,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(
                coutMouvementService.list(page, size, q, produitId, type, siteId, dateDebut, dateFin));
    }

    // ---------------------------------------------------------------- Valeur de stock

    @GetMapping("/valeur-stock")
    public ResponseEntity<ValeurStockDto> valeurStock(
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String categorieId,
            @RequestParam(required = false) PeriodeComparaison comparer) {
        return ResponseEntity.ok(valeurStockService.valeur(siteId, categorieId, comparer));
    }

    // ---------------------------------------------------------------- Coût par site

    @GetMapping("/cout-site")
    public ResponseEntity<ComparatifCoutSitesDto> coutSite(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String categorieId) {
        return ResponseEntity.ok(coutSiteService.comparatif(dateDebut, dateFin, categorieId));
    }

    // ---------------------------------------------------------------- Coût de revient chantier

    @GetMapping("/chantiers")
    public ResponseEntity<PageResponse<ChantierValoriseDto>> chantiers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StatutChantier statut,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(chantierService.list(page, size, q, statut, siteId, dateDebut, dateFin));
    }

    @GetMapping("/chantiers/{id}")
    public ResponseEntity<CoutRevientChantierDto> chantierDetail(@PathVariable String id) {
        return ResponseEntity.ok(chantierService.detail(id));
    }

    // ---------------------------------------------------------------- Marges

    @GetMapping("/marges")
    public ResponseEntity<SyntheseMargesDto> marges(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String categorieId) {
        return ResponseEntity.ok(margesService.synthese(dateDebut, dateFin, categorieId));
    }

    // ---------------------------------------------------------------- Tableau de bord financier

    @GetMapping("/tableau-bord")
    public ResponseEntity<RapportTableauBordFinancierDto> tableauBord(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String categorieId) {
        return ResponseEntity.ok(tableauBordService.rapport(dateDebut, dateFin, siteId, categorieId));
    }
}
