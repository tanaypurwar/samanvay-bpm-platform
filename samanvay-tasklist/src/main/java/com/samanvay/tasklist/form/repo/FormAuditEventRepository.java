package com.samanvay.tasklist.form.repo;

import com.samanvay.tasklist.form.entity.FormAuditEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormAuditEventRepository extends JpaRepository<FormAuditEvent, Long> {

  List<FormAuditEvent> findByFormDefinitionIdOrderByOccurredAtDesc(Long formDefinitionId);
}
