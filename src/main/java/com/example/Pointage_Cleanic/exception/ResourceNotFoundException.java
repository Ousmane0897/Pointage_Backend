package com.example.Pointage_Cleanic.exception;


import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException{ // Cette classe est utilisée pour renvoyé une exception lorsqu'une ressources ne se trouve pas dans la base de donnée

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {

        super(message);
    }


}

