package com.teamworkspace.backend;

/** Übernimmt Benutzername oder E-Mail und Passwort aus der Login-Anfrage; wird selbst nicht gespeichert. */
public class LoginRequest {

    // Das gemeinsame Feld erlaubt die Anmeldung mit E-Mail oder Benutzername.
    private String identifier;
    private String password;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
