package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ProduitBulkRequest;
import com.example.Pointage_Cleanic.Dto.stockv2.ProduitBulkResponse;
import com.example.Pointage_Cleanic.Dto.stockv2.ProduitDto;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.services.stockv2.ProduitStockService;
import com.example.Pointage_Cleanic.util.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/stock/produits")
@RequiredArgsConstructor
public class ProduitStockController {

    private final ProduitStockService service;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<PageResponse<ProduitDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TypeProduit typeProduit,
            @RequestParam(required = false) String categorieId,
            @RequestParam(required = false) String fournisseur,
            @RequestParam(required = false) Boolean sousSeuil,
            @RequestParam(required = false) Boolean actif
    ) {
        return ResponseEntity.ok(service.list(page, size, q, typeProduit, categorieId, fournisseur, sousSeuil, actif));
    }

    @GetMapping("/actifs")
    public ResponseEntity<List<ProduitDto>> actifs() {
        return ResponseEntity.ok(service.listActifs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProduitDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProduitDto> create(
            @RequestPart("produit") String produitJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "ficheTechnique", required = false) MultipartFile ficheTechnique
    ) throws IOException {
        ProduitDto dto = objectMapper.readValue(produitJson, ProduitDto.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto, photo, ficheTechnique));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProduitDto> update(
            @PathVariable String id,
            @RequestPart("produit") String produitJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "ficheTechnique", required = false) MultipartFile ficheTechnique,
            @RequestParam(value = "supprimerPhoto", defaultValue = "false") boolean supprimerPhoto,
            @RequestParam(value = "supprimerFicheTechnique", defaultValue = "false") boolean supprimerFicheTechnique
    ) throws IOException {
        ProduitDto dto = objectMapper.readValue(produitJson, ProduitDto.class);
        return ResponseEntity.ok(service.update(id, dto, photo, ficheTechnique, supprimerPhoto, supprimerFicheTechnique));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable String id) {
        ProduitStock p = service.getPhoto(id);
        String mime = p.getPhotoMimeType() == null ? MediaType.IMAGE_JPEG_VALUE : p.getPhotoMimeType();
        String nom = p.getPhotoNom() == null ? "photo" : p.getPhotoNom();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mime)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nom + "\"")
                .body(p.getPhoto());
    }

    @GetMapping("/{id}/fiche-technique")
    public ResponseEntity<byte[]> ficheTechnique(@PathVariable String id) {
        ProduitStock p = service.getFicheTechnique(id);
        String mime = p.getFicheTechniqueMimeType() == null ? MediaType.APPLICATION_PDF_VALUE : p.getFicheTechniqueMimeType();
        String nom = p.getFicheTechniqueNom() == null ? "fiche-technique.pdf" : p.getFicheTechniqueNom();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mime)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nom + "\"")
                .body(p.getFicheTechnique());
    }

    @PostMapping("/bulk")
    public ResponseEntity<ProduitBulkResponse> bulk(@RequestBody ProduitBulkRequest request) {
        ProduitBulkResponse response = service.importBulk(request);
        HttpStatus status = response.getFailed() == 0 ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(response);
    }
}
