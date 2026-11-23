package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.repositories.ProduitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seul reste de l'ancien {@code ProduitController} (stock historique, collection {@code produits}),
 * supprimé au lot 0 sécurité (C7) : ni le front ni aucun client connu n'appelait ses routes.
 *
 * <p>L'image reste <b>publique</b> faute de preuve qu'aucun client externe ne l'utilise : ses appels
 * sont comptés par {@code SuiviRoutesLegacyFilter}. Si le compteur reste à zéro, supprimer ce
 * contrôleur, son {@code permitAll} dans {@code SecurityConfig} et sa ligne dans
 * {@code RoutesPubliquesIT}.
 */
@RestController
@RequiredArgsConstructor
public class ProduitImageController {

    private final ProduitRepository produitRepository;

    @GetMapping("/api/produits/image/{codeProduit}")
    public ResponseEntity<byte[]> getProduitImage(@PathVariable String codeProduit) {
        return produitRepository.findByCodeProduit(codeProduit)
                .filter(p -> p.getImage() != null)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(p.getImage()))
                .orElse(ResponseEntity.notFound().build());
    }
}
