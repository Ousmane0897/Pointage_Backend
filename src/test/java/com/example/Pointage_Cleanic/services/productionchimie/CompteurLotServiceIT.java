package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.productionchimie.CompteurLot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class CompteurLotServiceIT extends MongoTestContainer {

    @Autowired
    private CompteurLotService service;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.remove(new Query(), CompteurLot.class);
    }

    @Test
    void cent_appels_concurrents_produisent_cent_numeros_distincts() throws Exception {
        int total = 100;
        ExecutorService exec = Executors.newFixedThreadPool(20);
        LocalDate fixed = LocalDate.of(2026, 5, 19);

        List<CompletableFuture<String>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < total; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> service.genererNumero(fixed), exec));
        }

        Set<String> numeros = new HashSet<>();
        Pattern pat = Pattern.compile("20260519-(\\d+)");
        int max = 0;
        for (CompletableFuture<String> f : futures) {
            String n = f.get(30, TimeUnit.SECONDS);
            assertThat(numeros.add(n)).as("numéro %s déjà vu", n).isTrue();
            Matcher m = pat.matcher(n);
            assertThat(m.matches()).isTrue();
            int v = Integer.parseInt(m.group(1));
            if (v > max) max = v;
        }
        exec.shutdownNow();

        assertThat(numeros).hasSize(total);
        assertThat(max).isEqualTo(total);
    }
}
