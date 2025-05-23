package com.example.Pointage_Cleanic.configurations;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfiguration { //Utiliser pour la sérialisation et la désérialisation des objets JSON.

    @Bean
    public ObjectMapper getJsonMapper() {
        return JsonMapper
                .builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .findAndAddModules()
                .build();
    }
    //La sérialisation est le processus de conversion d’un objet (ou d’une structure de données) en un format
    // qui peut être facilement stocké (dans un fichier, une base de données, etc.) ou transmis (par un réseau, par exemple).
    // Le format peut être du JSON, XML, binaire, etc. C’est l’une des principales utilisations de JsonMapper.

}
