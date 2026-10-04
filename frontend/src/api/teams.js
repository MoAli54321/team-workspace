import { authenticatedFetch } from './request.js'

// Lädt die eigenen Teams oder erstellt ein Team; die HTTP-Methode wird über options übergeben.
export function requestTeams(options = {}) {
  return authenticatedFetch('/api/teams', options)
}

// Einzelne Teamaktionen wie das Löschen verwenden die Team-ID im Pfad.
export function requestTeam(teamId, options = {}) {
  return authenticatedFetch(`/api/teams/${encodeURIComponent(teamId)}`, options)
}

// GET lädt die Mitgliederliste, POST fügt einen bereits registrierten Benutzer hinzu.
// Beim Entfernen wird die Benutzer-ID verwendet, nicht die ID des Mitgliedschaftseintrags.
export function requestTeamMembers(teamId, options = {}) {
  return authenticatedFetch(`/api/teams/${encodeURIComponent(teamId)}/members`, options)
}

export function requestTeamMember(teamId, userId, options = {}) {
  return authenticatedFetch(`/api/teams/${encodeURIComponent(teamId)}/members/${encodeURIComponent(userId)}`, options)
}
