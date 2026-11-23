package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.stock.Produit;
import com.example.Pointage_Cleanic.repositories.ProduitRepository;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProduitImageController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProduitImageControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProduitRepository produitRepository;

    // === Sécurité (OBLIGATOIRE en WebMvcTest)
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    @Test
    void image_ok() throws Exception {
        Produit p = new Produit();
        p.setImage("image".getBytes());
        when(produitRepository.findByCodeProduit("P001")).thenReturn(Optional.of(p));

        mockMvc.perform(get("/api/produits/image/P001"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void image_absente_404() throws Exception {
        when(produitRepository.findByCodeProduit("P002")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/produits/image/P002"))
                .andExpect(status().isNotFound());
    }

    @Test
    void les_autres_routes_de_l_ancien_controleur_n_existent_plus() throws Exception {
        mockMvc.perform(get("/api/produits/all")).andExpect(status().isNotFound());
    }
}
