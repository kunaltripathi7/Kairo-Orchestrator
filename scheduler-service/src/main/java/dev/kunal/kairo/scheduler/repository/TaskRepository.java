package dev.kunal.kairo.scheduler.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.kunal.kairo.common.entity.Task;
import dev.kunal.kairo.common.enums.TaskStatus;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByWorkflowIdOrderBySequenceNumberAsc(UUID workflowId);

    List<Task> findByWorkflowId(UUID workflowId);

    List<Task> findByStatusAndNextRetryTimeLessThanEqual(TaskStatus status, Instant time);

    @Query("SELECT t FROM Task t WHERE t.status = :status AND (t.lockedUntil < :time OR (t.lockedUntil IS NULL AND t.updatedAt < :time))")
    List<Task> findStaleTasks(@Param("status") TaskStatus status, @Param("time") Instant time);

    /**
     * Atomically claims a task by setting its status, lockedBy, and lockedUntil fields.
     * Only succeeds if the task is currently in the expected status AND is not locked
     * by another node (lockedUntil is null or expired).
     * Returns 1 if claimed, 0 if already claimed by another node.
     */
    @Modifying
    @Query("""
            UPDATE Task t
            SET t.status = :newStatus,
                t.lockedBy = :nodeId,
                t.lockedUntil = :leaseUntil
            WHERE t.id = :taskId
              AND t.status = :expectedStatus
              AND (t.lockedUntil IS NULL OR t.lockedUntil < :now)
            """)
    int claimTask(@Param("taskId") UUID taskId,
                  @Param("expectedStatus") TaskStatus expectedStatus,
                  @Param("newStatus") TaskStatus newStatus,
                  @Param("nodeId") String nodeId,
                  @Param("leaseUntil") Instant leaseUntil,
                  @Param("now") Instant now);

    /**
     * Releases the lock on a task after completion or failure.
     */
    @Modifying
    @Query("""
            UPDATE Task t
            SET t.lockedBy = NULL,
                t.lockedUntil = NULL
            WHERE t.id = :taskId
            """)
    void releaseLock(@Param("taskId") UUID taskId);
}
