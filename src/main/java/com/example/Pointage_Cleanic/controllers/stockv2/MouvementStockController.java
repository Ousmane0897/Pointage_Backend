package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.MouvementStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.services.stockv2.MouvementStockService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Consultation des mouvements de stock — <b>lecture seule</b>.
 *
 * <p>⚠ <b>Ne pas rétablir d'endpoint d'écriture ici.</b> Un {@code POST} existait et appliquait
 * directement les deltas de stock : il permettait de fabriquer du stock sans bon, sans validation et
 * sans historique de workflow, ce que tout le module 7.4 vise précisément à empêcher — « aucun
 * mouvement n'affecte le stock sans passer par le circuit de validation ».
 *
 * <p>Un mouvement ne naît donc plus que de trois chemins, tous tracés :
 * {@link com.example.Pointage_Cleanic.services.stockv2.MouvementBonGenerator} à la validation d'un
 * bon, {@code InventaireService.cloturer} pour les écarts d'inventaire, et l'import de produits pour
 * le stock initial.
 *
 * <p>Une correction de stock passe par un <b>inventaire</b> (écart justifié puis clôture) ou par la
 * <b>suppression définitive</b> d'un document erroné, qui contre-passe son effet.
 */
@RestController
@RequestMapping("/api/stock/mouvements")
@RequiredArgsConstructor
public class MouvementStockController {

    private final MouvementStockService service;

    @GetMapping
    public ResponseEntity<PageResponse<MouvementStockDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String produitId,
            @RequestParam(required = false) TypeMouvement type,
            @RequestParam(required = false) MotifMouvement motif,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin
    ) {
        return ResponseEntity.ok(service.list(page, size, q, produitId, type, motif, siteId, dateDebut, dateFin));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MouvementStockDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

}
