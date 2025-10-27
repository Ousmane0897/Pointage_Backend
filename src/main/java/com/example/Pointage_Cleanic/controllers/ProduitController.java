package com.example.Pointage_Cleanic.controllers;


import com.example.Pointage_Cleanic.Dto.ProduitDto;
import com.example.Pointage_Cleanic.entities.stock.Produit;
import com.example.Pointage_Cleanic.repositories.ProduitRepository;
import com.example.Pointage_Cleanic.services.ProduitService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/produits")
@RequiredArgsConstructor
public class ProduitController {

    private final ProduitRepository produitRepository;

    private final ProduitService produitService;


    /**
     *
     *new PageImpl<>(List.of(p))
     * List.of(p) crée une liste contenant un seul produit
     * PageImpl<> convertit cette liste en un objet Page<Produit>
     * Donc, même un seul produit sera retourné dans un format paginé compatible avec ton frontend Angular
     */

    // Retourne une page de produit contenant 20 produits
    @GetMapping
    public Page<Produit> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q) {

        // Si aucun mot-clé de recherche : on renvoie la page complète
        if (q == null || q.isBlank()) {
            Page<Produit> all = produitRepository.findAll(PageRequest.of(page, size));
            return new PageImpl<>(all.getContent(), all.getPageable(), all.getTotalElements());
        }

        // Sinon on cherche le produit par code
        return produitRepository.findByCodeProduit(q)
                .map(p -> new PageImpl<>(List.of(p), PageRequest.of(page, size), 1))
                .orElseGet(() -> {
                    Page<Produit> all = produitRepository.findAll(PageRequest.of(page, size));
                    return new PageImpl<>(all.getContent(), all.getPageable(), all.getTotalElements());
                });
    }

    /*
        @RequestParam	Indique à Spring que la donnée vient d’un paramètre de la requête HTTP (par exemple un champ formData envoyé par Angular ou un <input type="file">).
        value = "image"	Nom du champ attendu dans la requête. Il doit correspondre exactement à la clé utilisée côté frontend (formData.append('image', fichier)).
        required = false	Rend le paramètre optionnel → si aucun fichier n’est envoyé, Spring ne renvoie pas d’erreur.
        MultipartFile image	Objet Java représentant le fichier reçu : il contient le nom, le type MIME, la taille et le contenu binaire du fichier.
        ➡️ Quand Angular envoie le FormData, Spring détecte :
        produit 👉 correspond au JSON (converti automatiquement en UserCreateDTO)
        image 👉 correspond au fichier (converti en MultipartFile)
        MultipartFile = objet Java représentant le fichier (nom, type, contenu, taille…)

     */

    @PostMapping
    public ResponseEntity<Produit> createProduit(
            @RequestPart("produit") ProduitDto produitDto ,
            @RequestParam(value = "image", required = false) MultipartFile image // Signifie: Je veux récupérer, depuis la requête HTTP, le champ nommé image (par exemple un fichier envoyé via un formulaire) et le stocker dans une variable image de type MultipartFile
    ) {
        try {
            byte[] imageData = image != null ? image.getBytes() : null;

            Produit produit  = Produit.builder()
                    .codeProduit(produitDto.getCodeProduit())
                    .nomProduit(produitDto.getNomProduit())
                    .description(produitDto.getDescription())
                    .categorie(produitDto.getCategorie())
                    .destination(produitDto.getDestination())
                    .uniteDeMesure(produitDto.getUniteDeMesure())
                    .conditionnement(produitDto.getConditionnement())
                    .prixDeVente(produitDto.getPrixDeVente())
                    .emplacement(produitDto.getEmplacement())
                    .seuilMinimum(produitDto.getSeuilMinimum())
                    .statut(produitDto.getStatut())
                    .image(imageData)
                    .quantiteSnapshot(produitDto.getQuantiteSnapshot())
                    .build();

            return ResponseEntity.ok(produitRepository.save(produit));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/categorie")
    public ResponseEntity<Map<String, Object>> getProductsByCategory(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Map<String, Object> result = produitService.getProductsByCategory(category, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/all")
    public ResponseEntity<List<Produit>> getAll() {

        return ResponseEntity.ok(produitRepository.findAll());
    }

    @GetMapping("/getByNomProduit")
    public ResponseEntity<Produit> getByNomProduit(@RequestParam String nomProduit) {
         Produit produit = new Produit();
         boolean check = produitService.findByNomProduit(nomProduit).isPresent();


         if (check) {
              produit = produitService.findByNomProduit(nomProduit).get();
         }
        return ResponseEntity.ok(produit);
    }

    @GetMapping("/destination")
    public ResponseEntity<Map<String, Object>> getProductsByDestination(
            @RequestParam(required = false) String destination,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Map<String, Object> result = produitService.getProductsByDestination(destination, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/code")
    public ResponseEntity<Produit> getProductByCodeProduit(@RequestParam String codeProduit) {
        return produitService.findByCodeProduit(codeProduit)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }




    @GetMapping("/{id}")
    public Optional<Produit> get(@PathVariable String id) {
        return produitRepository.findById(id);
    }

    @GetMapping("/image/{codeProduit}")
    public ResponseEntity<byte[]> getProduitImage(@PathVariable String codeProduit) {
        return produitRepository.findByCodeProduit(codeProduit)
                .filter(p -> p.getImage() != null)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(p.getImage()))
                .orElse(ResponseEntity.notFound().build());
    }


    @PutMapping(value = "/{CodeProduit}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Produit update(@PathVariable String CodeProduit, @RequestBody Produit payload) {
        Produit existing = produitRepository.findByCodeProduit(CodeProduit).orElseThrow(() -> new IllegalArgumentException("Produit non trouvé"));
        existing.setNomProduit(payload.getNomProduit());
        existing.setDescription(payload.getDescription());
        existing.setCategorie(payload.getCategorie());
        existing.setUniteDeMesure(payload.getUniteDeMesure());
        existing.setConditionnement(payload.getConditionnement());
        existing.setPrixDeVente(payload.getPrixDeVente());
        existing.setEmplacement(payload.getEmplacement());
        existing.setSeuilMinimum(payload.getSeuilMinimum());
        existing.setStatut(payload.getStatut());
        return produitRepository.save(existing);
    }


    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        produitRepository.deleteById(id);
    }

}