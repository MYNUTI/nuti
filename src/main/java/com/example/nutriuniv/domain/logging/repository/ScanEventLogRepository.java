package com.example.nutriuniv.domain.logging.repository;

import com.example.nutriuniv.domain.logging.entity.ScanEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanEventLogRepository extends JpaRepository<ScanEventLog, Long> {
}
