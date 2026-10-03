package com.teamworkspace.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Lädt den Spring-Anwendungskontext einschließlich der konfigurierten Datenbankanbindung.
// Dafür müssen PostgreSQL und die Zugangsdaten aus application.properties verfügbar sein.
@SpringBootTest
class BackendApplicationTests {

	@Test
	void contextLoads() {
		// Der Test besteht, wenn der Anwendungskontext ohne Fehler aufgebaut werden kann.
	}

}
