package com.teamworkspace.backend;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Gemeinsame Eingabegrenzen für die Textspalten und Auswahlfelder der API. */
final class RequestValidation {

    private RequestValidation() {
    }

    static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " fehlt");
        }
        return optionalText(value, field);
    }

    static String optionalText(String value, String field) {
        if (value == null) return null;
        String text = value.strip();
        if (text.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " darf höchstens 255 Zeichen enthalten");
        }
        return text;
    }

    static void task(Task task) {
        task.setTitle(requiredText(task.getTitle(), "Titel"));
        task.setDescription(optionalText(task.getDescription(), "Beschreibung"));
        if (task.getStatus() == null || !Set.of("TODO", "IN_PROGRESS", "DONE").contains(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Status");
        }
        if (task.getPriority() == null || !Set.of("LOW", "MEDIUM", "HIGH").contains(task.getPriority())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Priorität");
        }
    }
}
