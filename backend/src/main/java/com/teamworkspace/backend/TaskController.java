package com.teamworkspace.backend;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verwaltet Aufgaben eines Projekts. Für jeden Zugriff zählt die Mitgliedschaft im zugehörigen Team,
 * auch wenn eine Aufgabe direkt über ihre ID bearbeitet oder gelöscht wird.
 */
@RestController
public class TaskController {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TeamMemberRepository teamMemberRepository;

    public TaskController(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            TeamMemberRepository teamMemberRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    public List<Task> getProjectTasks(
            @PathVariable Long projectId,
            @AuthenticationPrincipal Jwt jwt) {
        getProjectForMember(projectId, jwt);
        return taskRepository.findByProjectId(projectId);
    }

    @PostMapping("/api/projects/{projectId}/tasks")
    @Transactional
    public Task createTask(
            @PathVariable Long projectId,
            @RequestBody Task request,
            @AuthenticationPrincipal Jwt jwt) {
        Project project = getProjectForMember(projectId, jwt);
        RequestValidation.task(request);
        // Eine übermittelte ID darf keine bestehende Aufgabe überschreiben.
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(request.getStatus());
        task.setPriority(request.getPriority());
        task.setProject(project);
        return taskRepository.save(task);
    }

    @PutMapping("/api/tasks/{id}")
    @Transactional
    public Task updateTask(
            @PathVariable Long id,
            @RequestBody Task updatedTask,
            @AuthenticationPrincipal Jwt jwt) {
        Task task = getTaskForMember(id, jwt);
        RequestValidation.task(updatedTask);

        // Nur die bearbeitbaren Inhalte werden übernommen. Die Projektzuordnung bleibt erhalten.
        task.setTitle(updatedTask.getTitle());
        task.setDescription(updatedTask.getDescription());
        task.setStatus(updatedTask.getStatus());
        task.setPriority(updatedTask.getPriority());

        return taskRepository.save(task);
    }

    @DeleteMapping("/api/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteTask(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Task task = getTaskForMember(id, jwt);
        taskRepository.delete(task);
    }

    // Der Zugriff wird bei jeder Anfrage neu geprüft. Ein entferntes Mitglied verliert dadurch
    // den Zugang auch dann, wenn sein JWT noch nicht abgelaufen ist.
    private Project getProjectForMember(Long projectId, Jwt jwt) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Projekt nicht gefunden"));

        Long userId = getAuthenticatedUserId(jwt);
        Long teamId = project.getTeam().getId();
        if (!teamMemberRepository.existsByUserIdAndTeamId(userId, teamId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Kein Zugriff auf dieses Projekt");
        }

        return project;
    }

    private Task getTaskForMember(Long taskId, Jwt jwt) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Task nicht gefunden"));

        // Alte Aufgaben können noch ohne Projekt gespeichert sein. Ohne Teamzuordnung
        // lässt sich kein Zugriffsrecht prüfen, deshalb werden diese Aufgaben hier nicht freigegeben.
        if (task.getProject() == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Task ist keinem Projekt zugeordnet");
        }

        getProjectForMember(task.getProject().getId(), jwt);
        return task;
    }

    // Eine fehlende oder nicht numerische Benutzer-ID wird als ungültige Anmeldung behandelt.
    private Long getAuthenticatedUserId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NullPointerException | NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Ungültiger Benutzer im Token");
        }
    }
}
