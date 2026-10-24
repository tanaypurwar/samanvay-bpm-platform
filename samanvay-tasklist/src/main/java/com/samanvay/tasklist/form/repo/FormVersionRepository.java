package com.samanvay.tasklist.form.repo;

import com.samanvay.tasklist.form.entity.FormVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormVersionRepository extends JpaRepository<FormVersion, Long> {

  Optional<FormVersion> findFirstByFormDefinitionIdOrderByVersionDesc(Long formDefinitionId);

  List<FormVersion> findByFormDefinitionIdOrderByVersionDesc(Long formDefinitionId);
}
