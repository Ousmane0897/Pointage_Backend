package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.AuthRequest;
import com.example.Pointage_Cleanic.Dto.AuthResponse;
import com.example.Pointage_Cleanic.entities.Admins;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.SuperAdminRepository;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.LoginService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/login")
@RequiredArgsConstructor
public class LoginController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final MyUserDetailsService userDetailsService;
    private final LoginService loginService;
    private final LoginRepository loginRepository;
    private final SuperAdminRepository superAdminRepository;


    @PostMapping
    public ResponseEntity<?> login(@RequestBody AuthRequest authRequest) {

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword())
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body("Incorrect username or password");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(authRequest.getEmail());

        // First try to find in Admins
        Optional<Admins> adminOpt = superAdminRepository.findByEmail(authRequest.getEmail());

        if (adminOpt.isPresent()) {
            Admins admin = adminOpt.get();
            String jwt = jwtUtil.generateToken(userDetails,admin.getPrenom(), admin.getNom(), admin.getRole(), admin.getPoste() );
            return ResponseEntity.ok(new AuthResponse(jwt));
        }

        // Then try to find in SuperAdmin
        Optional<User> userOpt = loginRepository.findByEmail(authRequest.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String jwt = jwtUtil.generateToken2(userDetails, user.getRole());
            return ResponseEntity.ok(new AuthResponse(jwt));
        }

        // If email not found in either repo
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
    }



    @GetMapping
    public ResponseEntity<List<User>> get() {
       List<User>  user = loginRepository.findAll();
       return ResponseEntity.ok(user);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteEmployee(@PathVariable String id) {
        User user =  loginService.getById(id);

        if (user == null) {
            return ResponseEntity.notFound().build(); // 404
        }
        loginRepository.delete(user);
        Map<String, Boolean> response = new HashMap<>();
        response.put("Deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }
}
