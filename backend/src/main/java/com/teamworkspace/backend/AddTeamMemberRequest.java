package com.teamworkspace.backend;

/**
 * Benennt den hinzuzufügenden Benutzer über E-Mail oder Benutzername.
 * Eine Rolle wird nicht entgegengenommen: Neue Mitglieder erhalten im Controller immer MEMBER.
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
