package com.teamworkspace.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Lädt den Anwendungskontext mit eigener H2-Datenbank und einem separaten Testschlüssel.
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

	@Test
	void contextLoads() {
		// Der Test besteht, wenn der Anwendungskontext ohne Fehler aufgebaut werden kann.
	}

}
