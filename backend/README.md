# Backend: Login und JWT-Schutz der Task-API

Aktueller Stand: Spring Security ist eingerichtet und `POST /api/auth/login`
gibt nach erfolgreicher Passwortprüfung einen signierten JWT zurück.
`/api/tasks` und alle Unterpfade erfordern diesen Token im Bearer-Header.
Die Versionen der Security- und JWT-Module werden von Spring Boot verwaltet.

## Backend starten

Neben dem bestehenden `DB_PASSWORD` benötigt das Backend jetzt `JWT_SECRET`.
Der Schlüssel muss aus mindestens 32 zufälligen Bytes bestehen und als Base64
übergeben werden. Es gibt keinen eingebauten Ersatzschlüssel.

Für die lokale Entwicklung in PowerShell, aus dem Ordner `backend`:

```powershell
# DB_PASSWORD muss in dieser Shell bereits für deine PostgreSQL-Datenbank gesetzt sein.
$jwtKeyBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtKeyBytes)
$jwtRng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)

.\mvnw.cmd spring-boot:run
```

Den Schlüssel nicht ins Repository schreiben. Bei einem IDE-Start muss
`JWT_SECRET` in der lokalen Startkonfiguration gesetzt sein. Ein neuer
Signierschlüssel macht zuvor ausgestellte Tokens für die spätere Prüfung ungültig;
für dauerhafte Nutzung den einmal erzeugten Schlüssel sicher aufbewahren.
Eine `.env`-Datei wird von Spring Boot nicht automatisch geladen.

## Login-Antwort

Die bisherigen Felder bleiben erhalten; hinzu kommen `token`, `tokenType` und
`expiresIn` (Gültigkeitsdauer in Sekunden):

```json
{
  "message": "Login successful",
  "userId": 1,
  "username": "beispiel",
  "token": "<signierter JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Der Token enthält die Benutzer-ID (`sub`), den Benutzernamen (`username`),
den Aussteller (`iss`), die Ausstellungszeit (`iat`) und die Ablaufzeit (`exp`).
Die Signatur verwendet HS256. Der Inhalt ist lesbar und enthält kein Passwort
und keinen Passwort-Hash. Die Antwort darf nicht gecacht werden.

Falsche, unbekannte oder fehlende Zugangsdaten liefern weiterhin HTTP 401
ohne Token. Die Gültigkeitsdauer steht in `application.properties` unter
`jwt.expiration-seconds` und beträgt standardmäßig eine Stunde.

## Geschützte Task-API

`SecurityConfig` schützt `/api/tasks` und `/api/tasks/**` für alle HTTP-Methoden.
Die JWT-Prüfung übernimmt Spring Security mit `JwtService` als `JwtDecoder`.
Dabei werden HS256-Signatur, Aussteller `team-workspace` und die Zeitangaben
des Tokens geprüft. Die Signaturprüfung verwendet denselben `JWT_SECRET` wie
die Token-Erstellung beim Login.

| Anfrage auf `/api/tasks` | Ergebnis |
| --- | --- |
| Ohne Token | HTTP 401 |
| Mit ungültigem, manipuliertem oder abgelaufenem Token | HTTP 401 |
| Mit gültigem Login-Token | Zugriff auf die Task-API erlaubt |

Login, Registrierung und Health-Check bleiben ohne Anmeldung erreichbar.
Die API verwendet weder Session-Cookies noch HTTP-Basic- oder Formular-Login.
Die Task-Liste ist weiterhin gemeinsam; eine Zuordnung von Tasks zu einzelnen
Benutzern ist noch nicht eingerichtet.

Vue speichert den Token im `sessionStorage` über den Pinia-Auth-Store. Der
gemeinsame Helfer `frontend/src/api/tasks.js` sendet ihn bei allen Task-Anfragen
als `Authorization: Bearer <token>`. Bei HTTP 401 wird die abgewiesene Sitzung
im Frontend entfernt und das Dashboard leitet zum Login weiter.

Ein Router-Guard schützt die Dashboard-Navigation zusätzlich. Der Logout-Button
entfernt die Sitzung aus dem Pinia-Store und `sessionStorage` und führt zu
`/login`. Der Logout erfolgt lokal; bereits ausgestellte JWTs bleiben bis zu
ihrer Ablaufzeit bzw. einem Wechsel des Signierschlüssels serverseitig gültig.

Nach einem Neustart mit einem neuen Signierschlüssel ist eine erneute Anmeldung
erforderlich. Auch für den nächsten manuellen Start müssen `DB_PASSWORD` und
`JWT_SECRET` in der jeweiligen Shell bzw. IDE-Startkonfiguration gesetzt sein.

## Tests

```powershell
.\mvnw.cmd test
```

Die Spring-Tests aktivieren das Profil `test` mit einer flüchtigen H2-Datenbank
und einem separaten öffentlichen Testschlüssel. `DB_PASSWORD` und `JWT_SECRET`
werden für Tests nicht benötigt; die lokale PostgreSQL-Datenbank wird nicht
angesprochen. Das Testprofil liegt nur unter `src/test/resources` und wird nicht
in die ausführbare Anwendung gepackt.

Geprüft werden erfolgreiche und fehlgeschlagene Logins, Token-Inhalt, Signatur,
Ablaufzeit, ungültige Schlüssel sowie weiterhin öffentliche Registrierung und
Health-Check. Die HTTP-Tests verlangen 401 für unautorisierte Task-Anfragen
und prüfen Lesen, Anlegen, Bearbeiten und Löschen mit einem echten Login-Token.
