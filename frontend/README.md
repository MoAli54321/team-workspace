# .

This template should help get you started developing with Vue 3 in Vite.

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## Project Setup

```sh
npm install
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

### Compile and Minify for Production

```sh
npm run build
```

## Login und Auth-Store

Pinia wird in `src/main.js` eingebunden. Der Store `src/stores/auth.js` führt den
Login aus und speichert nach einer erfolgreichen Antwort `token`, `username` und
`userId` als gemeinsamen JSON-Eintrag `auth` im `sessionStorage`. Das Passwort
wird nicht gespeichert. Beim erneuten Erzeugen des Stores, beispielsweise nach
einem Neuladen, werden die Daten aus diesem Eintrag wiederhergestellt.

`LoginView.vue` leitet erst nach erfolgreichem Speichern zum Dashboard weiter.
Eine Antwort ohne Token, ein fehlgeschlagener Login oder ein Speicherfehler
wird als Fehlermeldung angezeigt. Das Backend muss dafür bereits mit der
JWT-Erzeugung und gesetztem `JWT_SECRET` neu gestartet worden sein; siehe
[Backend-Anleitung](../backend/README.md).

`src/api/tasks.js` liest bei jeder Task-Anfrage den aktuellen Token aus dem
Auth-Store und ergänzt `Authorization: Bearer <token>`. Alle GET-, POST-, PUT-
und DELETE-Anfragen aus dem Dashboard verwenden diesen Helfer. Ohne Token
wird kein leerer Bearer-Header gesendet. Ein HTTP 401 entfernt die abgewiesene
Sitzung; das Dashboard leitet daraufhin zum Login weiter. Eine verspätete
Antwort einer alten Sitzung meldet einen inzwischen neu angemeldeten Benutzer
nicht ab. Andere HTTP-Fehler behandelt weiterhin die jeweilige Task-Funktion.

Das Backend schützt `/api/tasks` und alle Unterpfade durch JWT-Prüfung.
Der Router-Guard in `src/router/authGuard.js` nutzt den Auth-Store:

- `/dashboard` erfordert über `meta.requiresAuth` eine lokale Sitzung mit einem
  lesbaren, noch nicht abgelaufenen JWT. Andernfalls geht es zu `/login`.
- Mit einer solchen Sitzung führen `/login` und `/register` zu `/dashboard`.
- Beschädigte oder abgelaufene Tokens verhindern die Navigation nicht.

Der Logout-Button im Dashboard leert über `authStore.logout()` den Store und
entfernt nur den Eintrag `auth` aus dem `sessionStorage`. Die Aufgabenanzeige
wird geleert und die aktuelle Route durch `/login` ersetzt. Ein erneuter
Dashboard-Aufruf oder Neuladen stellt die alte Sitzung nicht wieder her.

Die lokale Ablaufprüfung dient der Benutzerführung; die verbindliche
Signatur- und Ablaufprüfung bleibt im Backend. Logout beendet die lokale
Sitzung, widerruft aber keinen bereits ausgestellten JWT auf dem Server.

Die Frontend-Tests prüfen mit simulierten HTTP-Antworten und Sitzungsspeicher
das Speichern, Wiederherstellen, Logout, die Weiterleitungen mit Vue Router,
die Bearer-Header aller Task-Methoden und die Fehlerfälle:

```sh
npm test
```
