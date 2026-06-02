package com.restaurantplanner.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.restaurantplanner.audit.domain.AuditLog;
import com.restaurantplanner.audit.domain.AuditLogRepository;
import org.junit.jupiter.api.Test;

class AuditServiceTest {

    @Test
    void recordDoesNotPropagateRepositoryFailures() {
        AuditLogRepository repository = org.mockito.Mockito.mock(AuditLogRepository.class);
        doThrow(new RuntimeException("database unavailable")).when(repository).save(any(AuditLog.class));

        AuditService service = new AuditService(repository);

        assertDoesNotThrow(() -> service.record(1L, "Reservation", 10L, "reservation.updated", 99L, "{}"));
        verify(repository).save(any(AuditLog.class));
    }
}
