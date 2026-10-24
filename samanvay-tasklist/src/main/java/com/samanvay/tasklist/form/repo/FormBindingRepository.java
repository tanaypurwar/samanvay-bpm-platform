package com.samanvay.tasklist.form.repo;

import com.samanvay.tasklist.form.entity.FormBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormBindingRepository extends JpaRepository<FormBinding, Long> {

  List<FormBinding> findByTaskDefinitionKeyOrderByPriorityDesc(String taskDefinitionKey);

  List<FormBinding> findByFormDefinitionIdOrderByPriorityDesc(Long formDefinitionId);
}
