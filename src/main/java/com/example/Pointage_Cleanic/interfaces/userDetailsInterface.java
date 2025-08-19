package com.example.Pointage_Cleanic.interfaces;

import org.springframework.security.core.userdetails.UserDetails;

public interface userDetailsInterface {

    UserDetails loadUserByUsername(String username);
}
