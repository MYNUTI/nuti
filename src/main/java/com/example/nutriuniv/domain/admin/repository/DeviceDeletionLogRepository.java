package com.example.nutriuniv.domain.admin.repository;

import com.example.nutriuniv.domain.admin.entity.DeviceDeletionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceDeletionLogRepository extends JpaRepository<DeviceDeletionLog, Long> {
}
