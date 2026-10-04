# Team Workspace

Team Workspace is a full-stack web application for managing teams, projects, and tasks.

## Features

- User registration and login
- BCrypt password hashing
- JWT authentication
- Protected frontend routes and backend endpoints
- Team creation and owner-controlled deletion, including projects and tasks
- Team membership with `OWNER` and `MEMBER` roles
- Member list with owner-controlled adding and removal
- Project management inside teams
- Owner-controlled project deletion, including its tasks
- Task management inside projects
- Task status and priority
- PostgreSQL persistence
- Logout and session handling
- Password visibility toggle on login and registration

## Tech Stack

### Frontend

- Vue 3
- JavaScript
- Vue Router
- Pinia
- Vite

### Backend

- Java 17
- Spring Boot
- Spring Security
- Spring Data JPA
- JWT authentication

### Database

- PostgreSQL
- H2 for automated backend tests

### Testing

- Spring Boot integration tests
- Node-based frontend tests
- Vite production build

## Application Structure

```text
User
  -> Team
      -> Project
          -> Task
```

Users can only see teams they belong to. Team membership controls access to the team's projects and tasks. A user who creates a team becomes its `OWNER`; users added later become `MEMBER`s.

## Main Routes

### Frontend

- `/login`
- `/register`
- `/dashboard`
- `/teams/:teamId/projects`
- `/teams/:teamId/projects?tab=members`
- `/projects/:projectId/tasks`

### Backend

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET|POST /api/teams`
- `DELETE /api/teams/{teamId}`
- `GET|POST /api/teams/{teamId}/members`
- `DELETE /api/teams/{teamId}/members/{userId}`
- `GET|POST /api/teams/{teamId}/projects`
- `DELETE /api/teams/{teamId}/projects/{projectId}`
- `GET|POST /api/projects/{projectId}/tasks`
- `PUT|DELETE /api/tasks/{taskId}`

All team, project, and task endpoints require a valid JWT bearer token.

Open a team to switch between projects and members. Only the team owner can add or remove members and delete projects or the team. Adding a member requires an existing account, identified by username or email. Removing someone revokes their team access without deleting their account. The owner cannot be removed. Deleting a project removes its tasks; deleting a team removes all its projects, tasks, and memberships. The interface asks for confirmation before deleting.

## Local Development

### Requirements

- Java 17
- Node.js `^22.12.0` or `>=24.0.0`, as defined in `frontend/package.json`
- PostgreSQL with a database named `team_workspace`

### Backend

The backend reads these environment variables:

- `DB_PASSWORD`: password for the local PostgreSQL user `postgres`
- `JWT_SECRET`: Base64-encoded signing key containing at least 32 random bytes

From PowerShell:

```powershell
cd backend
$env:DB_PASSWORD = "<your PostgreSQL password>"

$jwtKeyBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtKeyBytes)
$jwtRng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtKeyBytes)

.\mvnw.cmd spring-boot:run
```

The backend runs at `http://localhost:8081`.

See [backend/README.md](backend/README.md) for API and security details.

### Frontend

In a second terminal:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

The frontend runs at `http://localhost:5173` and proxies `/api` requests to the backend.

## Tests

Backend:

```powershell
cd backend
.\mvnw.cmd test
```

Frontend:

```powershell
cd frontend
npm.cmd test
npm.cmd run build
```

## Status

Version 1.0 completed.
