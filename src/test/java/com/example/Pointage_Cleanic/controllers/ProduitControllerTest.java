package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.ProduitDto;
import com.example.Pointage_Cleanic.entities.stock.Produit;
import com.example.Pointage_Cleanic.repositories.ProduitRepository;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.ProduitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProduitController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProduitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProduitRepository produitRepository;

    @MockBean
    private ProduitService produitService;

    // === Sécurité (OBLIGATOIRE en WebMvcTest)
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    // =========================
    // GET /api/produits (pagination)
    // =========================
    @Test
    void list_produits_ok() throws Exception {
        Produit p = new Produit();
        when(produitRepository.findAll((Pageable) any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(p)));

        mockMvc.perform(get("/api/produits"))
                .andExpect(status().isOk());
    }

    // =========================
    // GET /api/produits?search
    // =========================
    @Test
    void list_produits_by_code_ok() throws Exception {
        Produit p = new Produit();
        p.setCodeProduit("P001");

        when(produitRepository.findByCodeProduit("P001"))
                .thenReturn(Optional.of(p));

        mockMvc.perform(get("/api/produits")
                        .param("q", "P001"))
                .andExpect(status().isOk());
    }

    // =========================
    // POST /api/produits (multipart)
    // =========================
    @Test
    void create_produit_with_image_ok() throws Exception {

        ProduitDto dto = new ProduitDto();
        dto.setCodeProduit("P001");
        dto.setNomProduit("Savon");

        MockMultipartFile produit =
                new MockMultipartFile(
                        "produit",
                        "",
                        "application/json",
                        objectMapper.writeValueAsBytes(dto)
                );

        MockMultipartFile image =
                new MockMultipartFile(
                        "image",
                        "image.jpg",
                        "image/jpeg",
                        "fake-image".getBytes()
                );

        when(produitRepository.save(any())).thenReturn(new Produit());

        mockMvc.perform(multipart("/api/produits")
                        .file(produit)
                        .file(image))
                .andExpect(status().isOk());
    }

    // =========================
    // GET /api/produits/all
    // =========================
    @Test
    void get_all_produits_ok() throws Exception {
        when(produitRepository.findAll())
                .thenReturn(List.of(new Produit()));

        mockMvc.perform(get("/api/produits/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================
    // GET /api/produits/code
    // =========================
    @Test
    void get_by_code_ok() throws Exception {
        when(produitService.findByCodeProduit("P001"))
                .thenReturn(Optional.of(new Produit()));

        mockMvc.perform(get("/api/produits/code")
                        .param("codeProduit", "P001"))
                .andExpect(status().isOk());
    }

    @Test
    void get_by_code_not_found() throws Exception {
        when(produitService.findByCodeProduit("X"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/produits/code")
                        .param("codeProduit", "X"))
                .andExpect(status().isNotFound());
    }

    // =========================
    // GET /api/produits/nom
    // =========================
    @Test
    void get_by_nom_ok() throws Exception {
        when(produitService.findByNomProduit("Savon"))
                .thenReturn(Optional.of(new Produit()));

        mockMvc.perform(get("/api/produits/nom")
                        .param("nomProduit", "Savon"))
                .andExpect(status().isOk());
    }

    // =========================
    // GET /api/produits/image/{code}
    // =========================
    @Test
    void get_image_ok() throws Exception {
        Produit p = new Produit();
        p.setImage("image".getBytes());

        when(produitRepository.findByCodeProduit("P001"))
                .thenReturn(Optional.of(p));

        mockMvc.perform(get("/api/produits/image/P001"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    // =========================
    // PUT /api/produits/{code}
    // =========================
    @Test
    void update_produit_ok() throws Exception {

        Produit payload = new Produit();
        payload.setNomProduit("Savon");

        MockMultipartFile produit =
                new MockMultipartFile(
                        "produit",
                        "",
                        MediaType.APPLICATION_JSON_VALUE,
                        objectMapper.writeValueAsBytes(payload)
                );

        MockMultipartFile image =
                new MockMultipartFile(
                        "image",
                        "image.jpg",
                        MediaType.IMAGE_JPEG_VALUE,
                        "fake".getBytes()
                );

        when(produitRepository.findByCodeProduit("P001"))
                .thenReturn(Optional.of(new Produit()));
        when(produitRepository.save(any()))
                .thenReturn(new Produit());

        mockMvc.perform(
                        multipart("/api/produits/P001")
                                .file(produit)
                                .file(image)
                                .with(req -> {
                                    req.setMethod("PUT");
                                    return req;
                                })
                )
                .andExpect(status().isOk());
    }



    // =========================
    // DELETE /api/produits/{id}
    // =========================
    @Test
    void delete_produit_ok() throws Exception {
        doNothing().when(produitRepository).deleteById("1");

        mockMvc.perform(delete("/api/produits/1"))
                .andExpect(status().isOk());
    }

    // =========================
    // GET /api/produits/categorie
    // =========================
    @Test
    void get_by_categorie_ok() throws Exception {
        when(produitService.getProductsByCategory(any(), anyInt(), anyInt()))
                .thenReturn(Map.of("content", List.of(), "total", 0));

        mockMvc.perform(get("/api/produits/categorie"))
                .andExpect(status().isOk());
    }

    // =========================
    // GET /api/produits/destination
    // =========================
    @Test
    void get_by_destination_ok() throws Exception {
        when(produitService.getProductsByDestination(any(), anyInt(), anyInt()))
                .thenReturn(Map.of("content", List.of(), "total", 0));

        mockMvc.perform(get("/api/produits/destination"))
                .andExpect(status().isOk());
    }
}
