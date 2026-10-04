package com.teamworkspace.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Einfacher Erreichbarkeitstest für die API; führt selbst keine Datenbankabfrage aus.
@RestController
@RequestMapping("/api")
public class HealthController {

    // GET /api/health liefert einen Text, wenn der Server diese Anfrage bearbeiten kann.
    @GetMapping("/health")
    public String health() {
        return "Team Workspace API is running";
    }
}
