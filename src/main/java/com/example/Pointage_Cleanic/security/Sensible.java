package com.example.Pointage_Cleanic.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Champ sensible (salaire, RIB, numéros sociaux, mot de passe…) : le journal d'audit l'écrit
 * masqué ({@code "***"}), avant comme après (lot 1 / 1b, décision D6). Le masquage à
 * l'affichage selon les droits viendra en 1g (D9).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Sensible {
}
