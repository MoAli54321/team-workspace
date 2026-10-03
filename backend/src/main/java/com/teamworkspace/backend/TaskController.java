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
import org.springframework.web.server.ResponseStatusException;

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
            @RequestBody Task task,
            @AuthenticationPrincipal Jwt jwt) {
        Project project = getProjectForMember(projectId, jwt);
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

        task.setTitle(updatedTask.getTitle());
        task.setDescription(updatedTask.getDescription());
        task.setStatus(updatedTask.getStatus());
        task.setPriority(updatedTask.getPriority());

        return taskRepository.save(task);
    }

    @DeleteMapping("/api/tasks/{id}")
    @Transactional
    public void deleteTask(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        Task task = getTaskForMember(id, jwt);
        taskRepository.delete(task);
    }

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

        if (task.getProject() == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Task ist keinem Projekt zugeordnet");
        }

        getProjectForMember(task.getProject().getId(), jwt);
        return task;
    }

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
