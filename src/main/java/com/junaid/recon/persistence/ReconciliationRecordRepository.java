package com.junaid.recon.persistence;

import com.junaid.recon.core.MatchStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long> {

    List<ReconciliationRecord> findByRunIdOrderByIdAsc(String runId);

    Page<ReconciliationRecord> findByStatusIn(Collection<MatchStatus> statuses, Pageable pageable);
}
