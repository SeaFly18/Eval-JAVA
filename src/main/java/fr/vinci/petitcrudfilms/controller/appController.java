package fr.vinci.petitcrudfilms.controller;

import jakarta.websocket.server.PathParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class appController {
    @GetMapping("/adresse/{adresse}")
    public ResponseEntity<String> getAdresse(
            @PathVariable String adresse
    ) {
        return ResponseEntity.ok(adresse);
    }
}
