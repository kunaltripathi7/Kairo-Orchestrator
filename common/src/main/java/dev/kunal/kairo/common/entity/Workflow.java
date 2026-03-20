package dev.kunal.kairo.common.entity;

import com.fasterxml.jackson.databind.JsonNode;

import dev.kunal.kairo.common.enums.WorkflowStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Entity
@Table(name = "workflows")
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.PENDING;

    private Integer maxRetries;

    private Integer taskTimeoutSeconds;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
