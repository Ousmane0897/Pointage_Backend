package com.example.Pointage_Cleanic.util;

import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class CodeSecretUtil {

    @Named("extractLast4")
    public String extractLast4(String value) {
        if (value == null) {
            return null;
        }
        return value.substring(Math.max(0, value.length() - 4));
    }
}
