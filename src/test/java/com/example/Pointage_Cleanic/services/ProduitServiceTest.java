package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.stock.Produit;
import com.example.Pointage_Cleanic.repositories.ProduitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ProduitServiceTest {

    @Mock
    private ProduitRepository produitRepository;

    @InjectMocks
    private ProduitService service;

    // -------------------------------------------------------
    // 1️⃣ saveProduit()
    // -------------------------------------------------------
    @Test
    void testSaveProduit() {
        Produit produit = new Produit();
        produit.setNomProduit("Savon");

        when(produitRepository.save(produit)).thenReturn(produit);

        Produit result = service.saveProduit(produit);

        assertNotNull(result);
        assertEquals("Savon", result.getNomProduit());
        verify(produitRepository).save(produit);
    }

    // -------------------------------------------------------
    // 2️⃣ getProduitById() : existe
    // -------------------------------------------------------
    @Test
    void testGetProduitById_Found() {
        Produit produit = new Produit();
        produit.setId("1");

        when(produitRepository.findById("1")).thenReturn(Optional.of(produit));

        Optional<Produit> result = service.getProduitById("1");

        assertTrue(result.isPresent());
        assertEquals("1", result.get().getId());
    }

    // -------------------------------------------------------
    // 3️⃣ getProduitById() : non trouvé
    // -------------------------------------------------------
    @Test
    void testGetProduitById_NotFound() {
        when(produitRepository.findById("XYZ")).thenReturn(Optional.empty());

        Optional<Produit> result = service.getProduitById("XYZ");

        assertFalse(result.isPresent());
    }

    // -------------------------------------------------------
    // 4️⃣ getProductsByCategory() : avec catégorie
    // -------------------------------------------------------
    @Test
    void testGetProductsByCategory_WithCategory() {

        Produit p = new Produit();
        p.setNomProduit("Gant");

        Page<Produit> page = new PageImpl<>(List.of(p));

        when(produitRepository.findByCategorieContainingIgnoreCase(eq("NETTOYAGE"), any(PageRequest.class)))
                .thenReturn(page);

        Map<String, Object> result = service.getProductsByCategory("NETTOYAGE", 0, 10);

        assertTrue(result.containsKey("content"));
        assertTrue(result.containsKey("total"));

        List<?> content = (List<?>) result.get("content");
        assertEquals(1, content.size());
        assertEquals(1L, result.get("total"));
    }

    // -------------------------------------------------------
    // 5️⃣ getProductsByCategory() : sans catégorie (null)
    // -------------------------------------------------------
    @Test
    void testGetProductsByCategory_NoCategory() {

        Produit p = new Produit();
        p.setNomProduit("Gant");

        Page<Produit> page = new PageImpl<>(List.of(p));

        when(produitRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Map<String, Object> result = service.getProductsByCategory(null, 0, 10);

        assertEquals(1, ((List<?>) result.get("content")).size());
        assertEquals(1L, result.get("total"));
    }

    // -------------------------------------------------------
    // 6️⃣ getProductsByDestination() : avec destination
    // -------------------------------------------------------
    @Test
    void testGetProductsByDestination_WithCategory() {

        Produit p = new Produit();
        p.setNomProduit("Gant");

        Page<Produit> page = new PageImpl<>(List.of(p));

        when(produitRepository.findByDestinationContainingIgnoreCase(eq("DAKAR"), any(PageRequest.class)))
                .thenReturn(page);

        Map<String, Object> result = service.getProductsByDestination("DAKAR", 0, 10);

        assertEquals(1, ((List<?>) result.get("content")).size());
        assertEquals(1L, result.get("total"));
    }

    // -------------------------------------------------------
    // 7️⃣ getProductsByDestination() : sans catégorie
    // -------------------------------------------------------
    @Test
    void testGetProductsByDestination_NoCategory() {

        Produit p = new Produit();

        Page<Produit> page = new PageImpl<>(List.of(p));

        when(produitRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Map<String, Object> result = service.getProductsByDestination("", 0, 10);

        assertEquals(1, ((List<?>) result.get("content")).size());
    }

    // -------------------------------------------------------
    // 8️⃣ findByCodeProduit()
    // -------------------------------------------------------
    @Test
    void testFindByCodeProduit() {
        Produit produit = new Produit();
        produit.setCodeProduit("P001");

        when(produitRepository.findByCodeProduit("P001")).thenReturn(Optional.of(produit));

        Optional<Produit> result = service.findByCodeProduit("P001");

        assertTrue(result.isPresent());
        assertEquals("P001", result.get().getCodeProduit());
    }

    // -------------------------------------------------------
    // 9️⃣ findByNomProduit()
    // -------------------------------------------------------
    @Test
    void testFindByNomProduit() {
        Produit produit = new Produit();
        produit.setNomProduit("Balai");

        when(produitRepository.findByNomProduit("Balai")).thenReturn(Optional.of(produit));

        Optional<Produit> result = service.findByNomProduit("Balai");

        assertTrue(result.isPresent());
        assertEquals("Balai", result.get().getNomProduit());
    }
}
