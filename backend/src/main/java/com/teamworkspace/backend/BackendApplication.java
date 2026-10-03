package com.teamworkspace.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Aktiviert die Spring-Boot-Konfiguration und die Suche nach Komponenten in diesem Paket
// und seinen Unterpaketen, beispielsweise nach Controllern und Repositories.
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		// Baut den Spring-Anwendungskontext auf und startet den eingebetteten Webserver.
		SpringApplication.run(BackendApplication.class, args);
	}

}
