package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.Enum.RoleSuperviseur;
import com.example.Pointage_Cleanic.entities.GestionModules.ModulesAutorises;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

//import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey secretKey;

    private final long EXPIRATION_TIME = 1000 * 60 * 60 * 8; // 8h
    //private final long EXPIRATION_TIME = 1000 * 60 * 2; // 2 minutes


    @PostConstruct
    public void init() {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(UserDetails userDetails, String prenom, String nom, RoleAdmin role, String email, String poste, boolean mustChangePassword, ModulesAutorises modules) {
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("prenom",prenom)
                .claim("nom", nom)
                .claim("role",role)
                .claim("email", email)
                .claim("poste",poste)
                .claim("mustChangePassword", mustChangePassword)
                .claim("modules", modules)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }



    public String generateToken2(UserDetails userDetails, String role, String email, boolean mustChangePassword) {
        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("role",role)
                .claim("email",email )
                .claim("mustChangePassword", mustChangePassword)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // ➤ Extraire TOUS les claims
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // ➤ Extraire le username (subject)
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    // ➤ Extraire le rôle (claim personnalisé)
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername());
    }
}
