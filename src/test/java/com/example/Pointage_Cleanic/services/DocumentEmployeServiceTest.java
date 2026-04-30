package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.DocumentEmployeDto;
import com.example.Pointage_Cleanic.Dto.ModifierDocumentRequest;
import com.example.Pointage_Cleanic.Dto.ValiderDocumentRequest;
import com.example.Pointage_Cleanic.Enum.CategorieDocument;
import com.example.Pointage_Cleanic.Enum.DecisionDocument;
import com.example.Pointage_Cleanic.Enum.StatutDocument;
import com.example.Pointage_Cleanic.Mapper.DocumentEmployeMapper;
import com.example.Pointage_Cleanic.Mapper.DocumentEmployeMapperImpl;
import com.example.Pointage_Cleanic.entities.DocumentEmploye;
import com.example.Pointage_Cleanic.entities.DossierEmploye;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.DocumentEmployeRepository;
import com.example.Pointage_Cleanic.repositories.DossierEmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentEmployeServiceTest {

    @Mock
    private DocumentEmployeRepository repository;

    @Spy
    private DocumentEmployeMapper mapper = new DocumentEmployeMapperImpl();

    @Mock
    private DossierEmployeRepository dossierEmployeRepository;

    @InjectMocks
    private DocumentEmployeService service;

    private DossierEmploye employe;
    private DocumentEmploye existing;

    @BeforeEach
    void setUp() {
        employe = DossierEmploye.builder()
                .id("emp1").nom("Diop").prenom("Mamadou").build();

        existing = DocumentEmploye.builder()
                .id("d1")
                .employeId("emp1")
                .employeNom("Diop")
                .employePrenom("Mamadou")
                .nom("CNI")
                .categorie(CategorieDocument.CNI)
                .statut(StatutDocument.EN_ATTENTE)
                .build();
    }

    @Test
    void upload_ok_snapshots_employe_and_defaults() throws Exception {
        when(dossierEmployeRepository.findById("emp1")).thenReturn(Optional.of(employe));
        when(repository.save(any(DocumentEmploye.class))).thenAnswer(inv -> {
            DocumentEmploye d = inv.getArgument(0);
            d.setId("new-id");
            return d;
        });

        DocumentEmployeDto input = DocumentEmployeDto.builder()
                .employeId("emp1").nom("CNI Mamadou").categorie(CategorieDocument.CNI).build();
        MultipartFile fichier = new MockMultipartFile(
                "fichier", "cni.pdf", "application/pdf", new byte[]{1, 2, 3});

        DocumentEmployeDto result = service.upload(input, fichier);

        ArgumentCaptor<DocumentEmploye> captor = ArgumentCaptor.forClass(DocumentEmploye.class);
        verify(repository).save(captor.capture());
        DocumentEmploye saved = captor.getValue();
        assertThat(saved.getEmployeNom()).isEqualTo("Diop");
        assertThat(saved.getEmployePrenom()).isEqualTo("Mamadou");
        assertThat(saved.getNom()).isEqualTo("CNI Mamadou");
        assertThat(saved.getStatut()).isEqualTo(StatutDocument.EN_ATTENTE);
        assertThat(saved.getDateUpload()).isNotNull();
        assertThat(saved.getTailleFichier()).isEqualTo(3L);
        assertThat(saved.getTypeMime()).isEqualTo("application/pdf");
        assertThat(saved.getFichierNom()).isEqualTo("cni.pdf");
        assertThat(saved.getFichier()).containsExactly(1, 2, 3);

        assertThat(result.getStatut()).isEqualTo(StatutDocument.EN_ATTENTE);
        assertThat(result.getFichierUrl()).contains("/telecharger");
    }

    @Test
    void upload_employe_inconnu_returns_404() {
        when(dossierEmployeRepository.findById("empX")).thenReturn(Optional.empty());

        DocumentEmployeDto input = DocumentEmployeDto.builder()
                .employeId("empX").nom("CNI").categorie(CategorieDocument.CNI).build();
        MultipartFile fichier = new MockMultipartFile("fichier", "f.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service.upload(input, fichier))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void upload_nom_blank_returns_400() {
        DocumentEmployeDto input = DocumentEmployeDto.builder()
                .employeId("emp1").nom("  ").categorie(CategorieDocument.CNI).build();
        MultipartFile fichier = new MockMultipartFile("fichier", "f.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service.upload(input, fichier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nom");
    }

    @Test
    void upload_categorie_null_returns_400() {
        DocumentEmployeDto input = DocumentEmployeDto.builder()
                .employeId("emp1").nom("CNI").build();
        MultipartFile fichier = new MockMultipartFile("fichier", "f.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service.upload(input, fichier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("categorie");
    }

    @Test
    void upload_fichier_vide_returns_400() {
        DocumentEmployeDto input = DocumentEmployeDto.builder()
                .employeId("emp1").nom("CNI").categorie(CategorieDocument.CNI).build();
        MultipartFile fichier = new MockMultipartFile("fichier", "f.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.upload(input, fichier))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fichier");
    }

    @Test
    void modifier_only_provided_fields() {
        when(repository.findById("d1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Tous les autres champs sont null → seul le commentaire est mis à jour.
        ModifierDocumentRequest request = new ModifierDocumentRequest(
                null, null, null, "signé recto-verso");

        DocumentEmployeDto result = service.modifier("d1", request);

        assertThat(result.getCommentaire()).isEqualTo("signé recto-verso");
        assertThat(result.getNom()).isEqualTo("CNI"); // inchangé
        assertThat(result.getCategorie()).isEqualTo(CategorieDocument.CNI); // inchangé
    }

    @Test
    void modifier_nom_blank_returns_400() {
        when(repository.findById("d1")).thenReturn(Optional.of(existing));

        ModifierDocumentRequest request = new ModifierDocumentRequest(
                "  ", null, null, null);

        assertThatThrownBy(() -> service.modifier("d1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nom");
    }

    @Test
    void valider_ok_passes_to_VALIDE_with_commentaire() {
        when(repository.findById("d1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DocumentEmployeDto result = service.valider("d1",
                new ValiderDocumentRequest(DecisionDocument.VALIDE, "OK"));

        assertThat(result.getStatut()).isEqualTo(StatutDocument.VALIDE);
        assertThat(result.getCommentaire()).isEqualTo("OK");
    }

    @Test
    void valider_revision_VALIDE_to_REFUSE_autorise() {
        existing.setStatut(StatutDocument.VALIDE);
        when(repository.findById("d1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DocumentEmployeDto result = service.valider("d1",
                new ValiderDocumentRequest(DecisionDocument.REFUSE, "erreur initiale"));

        assertThat(result.getStatut()).isEqualTo(StatutDocument.REFUSE);
        assertThat(result.getCommentaire()).isEqualTo("erreur initiale");
    }

    @Test
    void valider_statut_null_returns_400() {
        when(repository.findById("d1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.valider("d1",
                new ValiderDocumentRequest(null, "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deriverStatutAffiche_returns_EXPIRE_when_VALIDE_and_dateExpiration_passee() {
        existing.setStatut(StatutDocument.VALIDE);
        existing.setDateExpiration(LocalDate.now().minusDays(1));

        StatutDocument affiche = DocumentEmployeService.deriverStatutAffiche(existing);

        assertThat(affiche).isEqualTo(StatutDocument.EXPIRE);
    }

    @Test
    void deriverStatutAffiche_returns_EXPIRE_when_EN_ATTENTE_and_dateExpiration_passee() {
        existing.setStatut(StatutDocument.EN_ATTENTE);
        existing.setDateExpiration(LocalDate.now().minusDays(1));

        StatutDocument affiche = DocumentEmployeService.deriverStatutAffiche(existing);

        assertThat(affiche).isEqualTo(StatutDocument.EXPIRE);
    }

    @Test
    void deriverStatutAffiche_keeps_REFUSE_even_if_dateExpiration_passee() {
        existing.setStatut(StatutDocument.REFUSE);
        existing.setDateExpiration(LocalDate.now().minusDays(1));

        StatutDocument affiche = DocumentEmployeService.deriverStatutAffiche(existing);

        assertThat(affiche).isEqualTo(StatutDocument.REFUSE);
    }

    @Test
    void deriverStatutAffiche_keeps_VALIDE_when_dateExpiration_future() {
        existing.setStatut(StatutDocument.VALIDE);
        existing.setDateExpiration(LocalDate.now().plusYears(1));

        StatutDocument affiche = DocumentEmployeService.deriverStatutAffiche(existing);

        assertThat(affiche).isEqualTo(StatutDocument.VALIDE);
    }

    @Test
    void deriverStatutAffiche_keeps_status_when_dateExpiration_null() {
        existing.setStatut(StatutDocument.VALIDE);
        existing.setDateExpiration(null);

        StatutDocument affiche = DocumentEmployeService.deriverStatutAffiche(existing);

        assertThat(affiche).isEqualTo(StatutDocument.VALIDE);
    }

    @Test
    void delete_ok() {
        when(repository.findById("d1")).thenReturn(Optional.of(existing));

        service.delete("d1");

        verify(repository).delete(existing);
    }

    @Test
    void getFichier_returns_404_if_byte_array_empty() {
        existing.setFichier(new byte[0]);
        when(repository.findById("d1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.getFichier("d1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
