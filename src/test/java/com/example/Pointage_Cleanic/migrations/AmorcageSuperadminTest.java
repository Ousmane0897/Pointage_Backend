package com.example.Pointage_Cleanic.migrations;

import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C3 — le mot de passe initial du super-admin vient de l'environnement, jamais du code. */
class AmorcageSuperadminTest {

    private final LoginRepository logins = mock(LoginRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AmorcageSuperadmin loader = new AmorcageSuperadmin(logins, encoder);

    @Test
    void sans_variable_aucun_compte_cree() {
        when(logins.findByEmail(AmorcageSuperadmin.SUPERADMIN_EMAIL)).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(loader, "superadminInitialPassword", "");

        loader.seedSuperadmin();

        verify(logins, never()).save(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void avec_variable_compte_cree_avec_changement_obligatoire() {
        when(logins.findByEmail(AmorcageSuperadmin.SUPERADMIN_EMAIL)).thenReturn(Optional.empty());
        when(encoder.encode("valeur-factice")).thenReturn("HASH");
        ReflectionTestUtils.setField(loader, "superadminInitialPassword", "valeur-factice");

        loader.seedSuperadmin();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(logins).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("HASH");
        assertThat(captor.getValue().getRole()).isEqualTo("SUPERADMIN");
        assertThat(captor.getValue().isMustChangePassword()).isTrue();
    }

    @Test
    void compte_existant_inchange() {
        when(logins.findByEmail(AmorcageSuperadmin.SUPERADMIN_EMAIL)).thenReturn(Optional.of(new User()));
        ReflectionTestUtils.setField(loader, "superadminInitialPassword", "valeur-factice");

        loader.seedSuperadmin();

        verify(logins, never()).save(any());
    }
}
