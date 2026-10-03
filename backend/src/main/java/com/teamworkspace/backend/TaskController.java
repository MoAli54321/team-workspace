package com.teamworkspace.backend;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-Schnittstelle für Aufgaben: nimmt HTTP-Anfragen entgegen und nutzt das Repository
 * für Datenbankzugriffe. Spring wandelt zurückgegebene Aufgaben automatisch in JSON um.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRepository taskRepository;

    // Spring übergibt das Repository beim Erzeugen des Controllers (Dependency Injection).
    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // GET /api/tasks liefert alle gespeicherten Aufgaben als Liste.
    @GetMapping
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    // POST /api/tasks: @RequestBody wandelt den JSON-Body in ein Task-Objekt um.
    @PostMapping
    public Task createTask(@RequestBody Task task) {
        return taskRepository.save(task);
    }

    // DELETE /api/tasks/{id}: @PathVariable übernimmt die ID aus der URL.
    @DeleteMapping("/{id}")
    public void deleteTask(@PathVariable Long id) {
        taskRepository.deleteById(id);
    }
    // PUT /api/tasks/{id} ersetzt die bearbeitbaren Felder einer bestehenden Aufgabe.
    @PutMapping("/{id}")
    public Task updateTask(@PathVariable Long id, @RequestBody Task updatedTask) {

        // Ohne vorhandene Aufgabe wird die Verarbeitung mit einer Ausnahme abgebrochen.
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Alle vier Felder werden übernommen; ID und Erstellungszeit bleiben erhalten.
        // Der Client muss auch unveränderte Felder mitsenden, sonst werden sie hier null.
        task.setTitle(updatedTask.getTitle());
        task.setDescription(updatedTask.getDescription());
        task.setStatus(updatedTask.getStatus());
        task.setPriority(updatedTask.getPriority());

        // Speichert die Änderungen und gibt die aktualisierte Aufgabe an den Client zurück.
        return taskRepository.save(task);
    }
}
