package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.Enum.stockv2.ActionWorkflow;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDestinataire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.DestinataireBon;
import com.example.Pointage_Cleanic.entities.stockv2.EntreeHistorique;
import com.example.Pointage_Cleanic.entities.stockv2.LigneBon;
import com.example.Pointage_Cleanic.repositories.UserRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import com.example.Pointage_Cleanic.services.EmailService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Notification e-mail à la création d'un bon (7.4) : destinataires, contenu, et surtout
 * tolérance aux pannes — un incident SMTP ne doit jamais remonter à l'appelant.
 */
@ExtendWith(MockitoExtension.class)
class BonMailNotificationServiceTest {

    private static final String FRONT = "http://localhost:4200";

    @Mock
    private EmailService emailService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private BonMailNotificationService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", FRONT);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private User superAdmin(String email) {
        User u = new User();
        u.setEmail(email);
        u.setRole("SUPERADMIN");
        return u;
    }

    private Utilisateur controleur(String email) {
        Utilisateur u = new Utilisateur();
        u.setEmail(email);
        u.setRole(RoleAdmin.CONTROLEUR_STOCK);
        u.setActive(true);
        return u;
    }

    private BonSortie bonSortie() {
        BonSortie bon = BonSortie.builder()
                .id("64f0")
                .reference("BS-20260729-003")
                .type(TypeSortie.DISTRIBUTION_CHANTIER)
                .date(LocalDate.of(2026, 7, 29))
                .siteSourceNom("Dépôt Dakar")
                .destinataire(DestinataireBon.builder()
                        .type(TypeDestinataire.SITE)
                        .siteNom("Chantier Almadies")
                        .build())
                .lignes(List.of(LigneBon.builder().produitId("p1").quantite(2).montant(125000).build()))
                .montantTotal(125000)
                .build();
        bon.getHistorique().add(EntreeHistorique.builder()
                .action(ActionWorkflow.CREATION)
                .auteur("Ousmane Diouf")
                .date(LocalDateTime.now())
                .build());
        return bon;
    }

    private BonEntree bonEntree() {
        BonEntree bon = BonEntree.builder()
                .id("64f1")
                .reference("BE-20260729-001")
                .type(TypeEntree.ACHAT_FOURNISSEUR)
                .date(LocalDate.of(2026, 7, 29))
                .siteDestinationNom("Dépôt Dakar")
                .fournisseur("SENCHIMIE")
                .lignes(List.of(LigneBon.builder().produitId("p1").quantite(10).montant(50000).build()))
                .montantTotal(50000)
                .build();
        bon.getHistorique().add(EntreeHistorique.builder()
                .action(ActionWorkflow.CREATION)
                .auteur("Ousmane Diouf")
                .date(LocalDateTime.now())
                .build());
        return bon;
    }

    // ------------------------------------------------------------------
    // Destinataires
    // ------------------------------------------------------------------

    @Test
    void envoieUnMailParDestinataire() throws MessagingException {
        when(userRepository.findByRoleIgnoreCase("SUPERADMIN")).thenReturn(List.of(superAdmin("boss@cleanic.sn")));
        when(utilisateurRepository.findByRoleAndActiveTrue(RoleAdmin.CONTROLEUR_STOCK))
                .thenReturn(List.of(controleur("ctrl1@cleanic.sn"), controleur("ctrl2@cleanic.sn")));

        service.notifierCreationSortie(bonSortie());

        ArgumentCaptor<String> destinataires = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(3)).sendHtmlEmail(destinataires.capture(), anyString(), anyString());
        assertThat(destinataires.getAllValues())
                .containsExactlyInAnyOrder("boss@cleanic.sn", "ctrl1@cleanic.sn", "ctrl2@cleanic.sn");
    }

    @Test
    void dedoublonneLesAdressesQuelleQueSoitLaCasse() throws MessagingException {
        when(userRepository.findByRoleIgnoreCase("SUPERADMIN")).thenReturn(List.of(superAdmin("Boss@Cleanic.SN")));
        when(utilisateurRepository.findByRoleAndActiveTrue(RoleAdmin.CONTROLEUR_STOCK))
                .thenReturn(List.of(controleur(" boss@cleanic.sn ")));

        service.notifierCreationSortie(bonSortie());

        verify(emailService, times(1)).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    void ignoreLesAdressesVides() throws MessagingException {
        when(userRepository.findByRoleIgnoreCase("SUPERADMIN")).thenReturn(List.of(superAdmin(null)));
        when(utilisateurRepository.findByRoleAndActiveTrue(RoleAdmin.CONTROLEUR_STOCK))
                .thenReturn(List.of(controleur("   "), controleur("ctrl@cleanic.sn")));

        service.notifierCreationSortie(bonSortie());

        verify(emailService, times(1)).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    @Test
    void nEnvoieRienSansDestinataire() throws MessagingException {
        when(userRepository.findByRoleIgnoreCase("SUPERADMIN")).thenReturn(List.of());
        when(utilisateurRepository.findByRoleAndActiveTrue(RoleAdmin.CONTROLEUR_STOCK)).thenReturn(List.of());

        service.notifierCreationSortie(bonSortie());

        verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------------
    // Contenu
    // ------------------------------------------------------------------

    @Test
    void corpsSortieContientLeRecapitulatifEtLeLien() throws MessagingException {
        when(userRepository.findByRoleIgnoreCase("SUPERADMIN")).thenReturn(List.of(superAdmin("boss@cleanic.sn")));
        when(utilisateurRepository.findByRoleAndActiveTrue(RoleAdmin.CONTROLEUR_STOCK)).thenReturn(List.of());

        service.notifierCreationSortie(bonSortie());

        ArgumentCaptor<String> sujet = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtmlEmail(anyString(), sujet.capture(), corps.capture());

        assertThat(sujet.getValue()).isEqualTo("Nouveau bon de sortie BS-20260729-003");
        assertThat(corps.getValue())
                .contains("BS-20260729-003")
                .contains("Dépôt Dakar")
                .contains("Chantier Almadies")
                .contains("125 000 FCFA")
                .contains("Ousmane Diouf")
                .contains("Distribution chantier")
                .contains(FRONT + "/admin/stock-v2/controle-mouvements/bons-sortie/64f0");
    }

    @Test
    void corpsEntreeContientFournisseurEtLienEntree() throws MessagingException {
        when(userRepository.findByRoleIgnoreCase("SUPERADMIN")).thenReturn(List.of(superAdmin("boss@cleanic.sn")));
        when(utilisateurRepository.findByRoleAndActiveTrue(RoleAdmin.CONTROLEUR_STOCK)).thenReturn(List.of());

        service.notifierCreationEntree(bonEntree());

        ArgumentCaptor<String> sujet = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtmlEmail(anyString(), sujet.capture(), corps.capture());

        assertThat(sujet.getValue()).isEqualTo("Nouveau bon d'entrée BE-20260729-001");
        assertThat(corps.getValue())
                .contains("SENCHIMIE")
                .contains("50 000 FCFA")
                .contains(FRONT + "/admin/stock-v2/controle-mouvements/bons-entree/64f1");
    }

    @Test
    void ometLeLienSiUrlFrontNonConfiguree() throws MessagingException {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "");
        when(userRepository.findByRoleIgnoreCase("SUPERADMIN")).thenReturn(List.of(superAdmin("boss@cleanic.sn")));
        when(utilisateurRepository.findByRoleAndActiveTrue(RoleAdmin.CONTROLEUR_STOCK)).thenReturn(List.of());

        service.notifierCreationSortie(bonSortie());

        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtmlEmail(anyString(), anyString(), corps.capture());
        assertThat(corps.getValue())
                .doesNotContain("Ouvrir le bon")
                .contains("BS-20260729-003");
    }

    @Test
    void echappeLeHtmlDesValeursMetier() throws MessagingException {
        when(userRepository.findByRoleIgnoreCase("SUPERADMIN")).thenReturn(List.of(superAdmin("boss@cleanic.sn")));
        when(utilisateurRepository.findByRoleAndActiveTrue(RoleAdmin.CONTROLEUR_STOCK)).thenReturn(List.of());
        BonEntree bon = bonEntree();
        bon.setFournisseur("<script>alert(1)</script>");

        service.notifierCreationEntree(bon);

        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtmlEmail(anyString(), anyString(), corps.capture());
        assertThat(corps.getValue())
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    // ------------------------------------------------------------------
    // Tolérance aux pannes — le point critique
    // ------------------------------------------------------------------

    @Test
    void uneErreurSmtpNeRemontePasALAppelant() throws MessagingException {
        when(userRepository.findByRoleIgnoreCase("SUPERADMIN")).thenReturn(List.of(superAdmin("boss@cleanic.sn")));
        when(utilisateurRepository.findByRoleAndActiveTrue(RoleAdmin.CONTROLEUR_STOCK)).thenReturn(List.of());
        doThrow(new MessagingException("SMTP indisponible"))
                .when(emailService).sendHtmlEmail(anyString(), anyString(), anyString());

        assertThatCode(() -> service.notifierCreationSortie(bonSortie())).doesNotThrowAnyException();
    }

    @Test
    void unDestinataireEnEchecNEmpechePasLesAutres() throws MessagingException {
        when(userRepository.findByRoleIgnoreCase("SUPERADMIN")).thenReturn(List.of(superAdmin("boss@cleanic.sn")));
        when(utilisateurRepository.findByRoleAndActiveTrue(RoleAdmin.CONTROLEUR_STOCK))
                .thenReturn(List.of(controleur("ctrl@cleanic.sn")));
        doThrow(new MessagingException("boîte pleine"))
                .when(emailService).sendHtmlEmail(eq("boss@cleanic.sn"), anyString(), anyString());

        service.notifierCreationSortie(bonSortie());

        verify(emailService).sendHtmlEmail(eq("ctrl@cleanic.sn"), anyString(), anyString());
    }

    @Test
    void unePanneDeBaseNeRemontePasALAppelant() {
        when(userRepository.findByRoleIgnoreCase("SUPERADMIN")).thenThrow(new RuntimeException("Mongo down"));

        assertThatCode(() -> service.notifierCreationSortie(bonSortie())).doesNotThrowAnyException();
    }

    @Test
    void bonNullEstIgnore() throws MessagingException {
        service.notifierCreationSortie(null);
        service.notifierCreationEntree(null);

        verify(emailService, never()).sendHtmlEmail(any(), any(), any());
    }
}
