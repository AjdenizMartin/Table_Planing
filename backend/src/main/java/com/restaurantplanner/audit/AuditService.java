package com.restaurantplanner.audit;

import com.restaurantplanner.audit.domain.AuditLog;
import com.restaurantplanner.audit.domain.AuditLogRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(Long restaurantId, String entityType, Long entityId, String action, Long userId, String metadataJson) {
        try {
            AuditLog auditLog = new AuditLog(restaurantId, entityType, entityId, action, userId, metadataJson);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to record audit log: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findByRestaurantId(Long restaurantId, int limit) {
        return auditLogRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId, PageRequest.of(0, limit));
    }
}
