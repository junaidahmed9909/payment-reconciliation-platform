package com.junaid.recon.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, String> {

    List<ReconciliationRun> findTop20ByOrderByCreatedAtDesc();
}
