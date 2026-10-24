package com.samanvay.tasklist.form.repo;

import com.samanvay.tasklist.form.entity.FormDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormDefinitionRepository extends JpaRepository<FormDefinition, Long> {

  Optional<FormDefinition> findByFormKey(String formKey);

  boolean existsByFormKey(String formKey);

  List<FormDefinition> findAllByOrderByNameAsc();
}
