# Team Workspace Frontend

Die Oberfläche verwendet Vue 3, JavaScript, Vue Router und Pinia.

## Lokal starten

Voraussetzung: Node.js gemäß `engines` in `package.json` und ein laufendes Backend
auf Port 8081. Im Ordner `frontend`:

```powershell
npm.cmd ci
npm.cmd run dev
```

Die Anwendung ist unter `http://localhost:5173` erreichbar. Vite leitet Anfragen
an `/api` an das Backend weiter. Die `.cmd`-Befehle funktionieren auch dann, wenn
PowerShell die Ausführung von `npm.ps1` blockiert.

## Seiten und Bedienung

| Route | Inhalt |
| --- | --- |
| `/login` | Anmeldung mit Benutzername oder E-Mail |
| `/register` | Konto erstellen |
| `/dashboard` | Eigene Teams anzeigen und erstellen; Besitzer können Teams löschen |
| `/teams/:teamId/projects` | Projekte anzeigen, erstellen und als Besitzer löschen |
| `/teams/:teamId/projects?tab=members` | Mitglieder anzeigen; Besitzer können Personen hinzufügen und entfernen |
| `/projects/:projectId/tasks` | Aufgaben erstellen, bearbeiten und löschen |

Alle Teammitglieder dürfen Projekte erstellen und Aufgaben bearbeiten. Vor dem
Löschen erscheint eine Bestätigung. Das Löschen eines Teams entfernt auch seine
Projekte und Aufgaben; das Löschen eines Projekts entfernt dessen Aufgaben.
Benutzerkonten bleiben beim Entfernen von Mitgliedschaften erhalten.

## Codeaufbau

- `views/` enthält die Seiten und ihre Formulare.
- `components/` enthält den gemeinsamen Seitenrahmen, Symbole und Löschdialog.
- `api/` enthält die Aufrufe für Teams, Projekte und Aufgaben.
- `stores/auth.js` verwaltet Anmeldung und Abmeldung.
- `router/authGuard.js` leitet bei fehlender oder abgelaufener Sitzung zum Login.
- `assets/` enthält gemeinsame Farben, Abstände und Regeln für schmale Bildschirme.

Der Auth-Store speichert Token, Benutzername und Benutzer-ID gemeinsam im
`sessionStorage`; das Passwort wird nicht gespeichert. Der API-Helfer ergänzt
den Bearer-Header. Eine Antwort mit HTTP 401 beendet die betroffene lokale Sitzung.
Die verbindliche Prüfung von JWT und Teamrechten erfolgt im Backend.

## Prüfen und bauen

```powershell
npm.cmd test
npm.cmd run build
```

Die automatisierten Tests prüfen Auth-Store, Router-Guard und API-Helfer mit
simulierten Antworten. Sie sind keine vollständigen Browser-Tests. Der Build
landet in `dist/`. Der Vite-Proxy gilt nur für die lokale Entwicklung.

Weitere Startvoraussetzungen stehen in der [Root-README](../README.md) und der
[Backend-Anleitung](../backend/README.md).
