package com.example.Pointage_Cleanic.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    /** Fuseau métier de l'application (Dakar = UTC+0, sans heure d'été). */
    public static final ZoneId ZONE_METIER = ZoneId.of("Africa/Dakar");

    /**
     * Horloge ancrée sur le fuseau métier, et non sur celui de la JVM.
     * <p>
     * Les entités sont datées en {@link java.time.LocalDateTime} : un
     * {@code Clock.systemDefaultZone()} sur une JVM hors Dakar produirait un
     * {@code now()} décalé, et donc des comparaisons de créneaux fausses. L'attribut
     * {@code zone} de {@code @Scheduled} ne corrige pas ce point : il ne pilote que
     * l'heure de déclenchement, jamais la valeur de {@code now}.
     */
    @Bean
    public Clock clock() {
        return Clock.system(ZONE_METIER);
    }
}
