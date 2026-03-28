package dev.kunal.kairo.scheduler.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.kunal.kairo.common.entity.Task;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByWorkflowIdOrderBySequenceNumberAsc(UUID workflowId);
}
