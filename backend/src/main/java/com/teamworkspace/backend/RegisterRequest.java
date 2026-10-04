package com.teamworkspace.backend;

/**
 * Überträgt die Daten aus dem JSON-Body einer Registrierungsanfrage (DTO).
 * Dieses Anfrageobjekt ist keine Datenbank-Entität; erst der Controller erstellt daraus einen User.
 */
public class RegisterRequest {

    private String username;
    private String email;
    // Klartext aus der Anfrage; der Controller erzeugt daraus vor dem Speichern einen BCrypt-Hash.
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
