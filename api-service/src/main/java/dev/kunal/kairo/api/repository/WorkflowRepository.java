package dev.kunal.kairo.api.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.kunal.kairo.common.entity.Workflow;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

}
