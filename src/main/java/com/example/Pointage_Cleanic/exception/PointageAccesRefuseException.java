package com.example.Pointage_Cleanic.exception;

/** Action d'administration du pointage hors des rôles RH / SUPERADMIN — traduite en 403. */
public class PointageAccesRefuseException extends RuntimeException {

    public PointageAccesRefuseException(String message) {
        super(message);
    }
}
