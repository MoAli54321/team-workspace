package com.teamworkspace.backend;

/**
 * Enthält die Daten, die der Client sendet,
 * wenn ein Benutzer zu einem Team hinzugefügt werden soll.
 */
public class AddTeamMemberRequest {

    private String identifier;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
}
