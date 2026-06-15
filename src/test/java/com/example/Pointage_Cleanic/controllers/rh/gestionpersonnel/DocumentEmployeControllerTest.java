package com.example.Pointage_Cleanic.controllers.rh.gestionpersonnel;

import com.example.Pointage_Cleanic.Dto.rh.DocumentEmployeDto;
import com.example.Pointage_Cleanic.Enum.rh.CategorieDocument;
import com.example.Pointage_Cleanic.Enum.rh.StatutDocument;
import com.example.Pointage_Cleanic.entities.rh.DocumentEmploye;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.rh.DocumentEmployeService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentEmployeController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentEmployeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentEmployeService service;

    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private DocumentEmployeDto buildDto() {
        return DocumentEmployeDto.builder()
                .id("d1")
                .employeId("emp1")
                .employeNom("Diop")
                .employePrenom("Mamadou")
                .nom("CNI Mamadou")
                .categorie(CategorieDocument.CNI)
                .fichierUrl("/api/gestion-personnel/documents/d1/telecharger")
                .tailleFichier(1024L)
                .typeMime("application/pdf")
                .dateUpload(LocalDateTime.of(2026, 4, 29, 10, 0))
                .statut(StatutDocument.EN_ATTENTE)
                .build();
    }

    @Test
    void list_ok() throws Exception {
        Page<DocumentEmployeDto> page = new PageImpl<>(List.of(buildDto()), PageRequest.of(0, 10), 1);
        Mockito.when(service.list(0, 10, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/gestion-personnel/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("d1"))
                .andExpect(jsonPath("$.content[0].categorie").value("CNI"));
    }

    @Test
    void list_filtre_employe_et_categorie() throws Exception {
        Page<DocumentEmployeDto> page = new PageImpl<>(List.of(buildDto()), PageRequest.of(0, 10), 1);
        Mockito.when(service.list(0, 10, "emp1", "CNI")).thenReturn(page);

        mockMvc.perform(get("/api/gestion-personnel/documents")
                        .param("employeId", "emp1")
                        .param("categorie", "CNI"))
                .andExpect(status().isOk());

        Mockito.verify(service).list(0, 10, "emp1", "CNI");
    }

    @Test
    void getByEmployeId_ok() throws Exception {
        Mockito.when(service.listByEmploye("emp1")).thenReturn(List.of(buildDto()));

        mockMvc.perform(get("/api/gestion-personnel/documents/employe/emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("d1"));
    }

    @Test
    void upload_ok() throws Exception {
        Mockito.when(service.upload(Mockito.any(), Mockito.any())).thenReturn(buildDto());

        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "cni.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/gestion-personnel/documents")
                        .file(fichier)
                        .param("employeId", "emp1")
                        .param("nom", "CNI")
                        .param("categorie", "CNI"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("d1"));
    }

    @Test
    void upload_employe_inconnu_returns_404() throws Exception {
        Mockito.when(service.upload(Mockito.any(), Mockito.any()))
                .thenThrow(new ResourceNotFoundException("Dossier employé introuvable : empX"));

        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "f.pdf", "application/pdf", new byte[]{1});

        mockMvc.perform(multipart("/api/gestion-personnel/documents")
                        .file(fichier)
                        .param("employeId", "empX")
                        .param("nom", "X")
                        .param("categorie", "AUTRE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_categorie_manquante_returns_400() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "f.pdf", "application/pdf", new byte[]{1});

        mockMvc.perform(multipart("/api/gestion-personnel/documents")
                        .file(fichier)
                        .param("employeId", "emp1")
                        .param("nom", "X"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_metadata_ok() throws Exception {
        DocumentEmployeDto updated = buildDto();
        updated.setNom("CNI corrigé");
        Mockito.when(service.modifier(Mockito.eq("d1"), Mockito.any())).thenReturn(updated);

        mockMvc.perform(put("/api/gestion-personnel/documents/d1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"CNI corrigé\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("CNI corrigé"));
    }

    @Test
    void valider_ok() throws Exception {
        DocumentEmployeDto valide = buildDto();
        valide.setStatut(StatutDocument.VALIDE);
        Mockito.when(service.valider(Mockito.eq("d1"), Mockito.any())).thenReturn(valide);

        mockMvc.perform(put("/api/gestion-personnel/documents/d1/valider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"VALIDE\",\"commentaire\":\"OK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VALIDE"));
    }

    @Test
    void valider_decision_invalide_returns_400() throws Exception {
        Mockito.when(service.valider(Mockito.eq("d1"), Mockito.any()))
                .thenThrow(new IllegalArgumentException("statut est obligatoire"));

        mockMvc.perform(put("/api/gestion-personnel/documents/d1/valider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_ok() throws Exception {
        mockMvc.perform(delete("/api/gestion-personnel/documents/d1"))
                .andExpect(status().isNoContent());
        Mockito.verify(service).delete("d1");
    }

    @Test
    void download_ok() throws Exception {
        DocumentEmploye doc = DocumentEmploye.builder()
                .id("d1")
                .fichier(new byte[]{1, 2, 3})
                .fichierNom("cni.pdf")
                .typeMime("application/pdf")
                .build();
        Mockito.when(service.getFichier("d1")).thenReturn(doc);

        mockMvc.perform(get("/api/gestion-personnel/documents/d1/telecharger"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"cni.pdf\""));
    }

    @Test
    void download_not_found_returns_404() throws Exception {
        Mockito.when(service.getFichier("inconnu"))
                .thenThrow(new ResourceNotFoundException("Document introuvable : inconnu"));

        mockMvc.perform(get("/api/gestion-personnel/documents/inconnu/telecharger"))
                .andExpect(status().isNotFound());
    }
}
