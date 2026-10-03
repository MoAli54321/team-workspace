# Team Workspace Backend

Das Backend stellt die geschützte REST-API für Benutzer, Teams, Projekte und Aufgaben bereit. Spring Security prüft JWT-Bearer-Tokens, Spring Data JPA speichert die Daten in PostgreSQL.

## Backend starten

Voraussetzungen:

- Java 17
- PostgreSQL mit der Datenbank `team_workspace`
- PostgreSQL-Benutzer `postgres`

Das Backend benötigt zwei Umgebungsvariablen:

- `DB_PASSWORD`: Passwort des lokalen PostgreSQL-Benutzers
- `JWT_SECRET`: mindestens 32 zufällige Bytes, Base64-kodiert

PowerShell im Ordner `backend`:

```powershell
$env:DB_PASSWORD = "<dein PostgreSQL-Passwort>"

$jwtKeyBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtKeyBytes)
$jwtRng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)

.\mvnw.cmd spring-boot:run
```

Der Server läuft anschließend unter `http://localhost:8081`. Geheimnisse gehören weder in `application.properties` noch ins Repository. Eine `.env`-Datei wird von Spring Boot nicht automatisch geladen.

## Authentifizierung

Registrierung und Login sind öffentlich:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/health`

Ein erfolgreicher Login liefert neben Benutzer-ID und Benutzername einen signierten JWT:

```json
{
  "message": "Login successful",
  "userId": 1,
  "username": "beispiel",
  "token": "<signed JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Geschützte Anfragen senden den Token so:

```http
Authorization: Bearer <token>
```

Fehlende, ungültige, manipulierte oder abgelaufene Tokens führen zu HTTP 401. Der Token verwendet HS256, den Aussteller `team-workspace` und standardmäßig eine Gültigkeitsdauer von einer Stunde.

## Geschützte API

| Methode | Route | Funktion |
| --- | --- | --- |
| `GET` | `/api/teams` | Teams des eingeloggten Benutzers laden |
| `POST` | `/api/teams` | Team erstellen; Ersteller wird `OWNER` |
| `POST` | `/api/teams/{teamId}/members` | Benutzer als `MEMBER` hinzufügen; nur für `OWNER` |
| `GET` | `/api/teams/{teamId}/projects` | Projekte eines Teams laden |
| `POST` | `/api/teams/{teamId}/projects` | Projekt im Team erstellen |
| `GET` | `/api/projects/{projectId}/tasks` | Aufgaben eines Projekts laden |
| `POST` | `/api/projects/{projectId}/tasks` | Aufgabe im Projekt erstellen |
| `PUT` | `/api/tasks/{taskId}` | Aufgabe bearbeiten |
| `DELETE` | `/api/tasks/{taskId}` | Aufgabe löschen |

Für Team-, Projekt- und Task-Zugriffe muss der eingeloggte Benutzer Mitglied des betroffenen Teams sein. Unerlaubte Zugriffe führen zu HTTP 403, unbekannte Ressourcen zu HTTP 404.

## Tests

```powershell
.\mvnw.cmd test
```

Die automatisierten Tests verwenden das Profil `test` mit einer flüchtigen H2-Datenbank und einem separaten Testschlüssel. Deshalb werden `DB_PASSWORD` und `JWT_SECRET` für den Testlauf nicht benötigt und die lokale PostgreSQL-Datenbank wird nicht verändert.

Die Tests prüfen Registrierung, Login, JWT-Validierung, geschützte Routen, Team-Mitgliedschaften und Rollen sowie Projekt- und Task-Zugriffe einschließlich 401, 403 und 404.
