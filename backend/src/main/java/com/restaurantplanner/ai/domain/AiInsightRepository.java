package com.restaurantplanner.ai.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiInsightRepository extends JpaRepository<AiInsight, Long> {

    @Query("""
        select i
        from AiInsight i
        where i.restaurant.id = :restaurantId
          and i.date = :date
        order by i.dismissed asc, i.createdAt desc
        """)
    List<AiInsight> findByRestaurantIdAndDateOrderByDismissedAscSeverityDescCreatedAtDesc(
        @Param("restaurantId") Long restaurantId,
        @Param("date") LocalDate date
    );

    @Query("""
        select i
        from AiInsight i
        where i.id = :insightId
          and i.restaurant.id = :restaurantId
        """)
    Optional<AiInsight> findByIdAndRestaurantId(
        @Param("insightId") Long insightId,
        @Param("restaurantId") Long restaurantId
    );

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        delete
        from AiInsight i
        where i.restaurant.id = :restaurantId
          and i.date = :date
        """)
    void deleteByRestaurantIdAndDate(
        @Param("restaurantId") Long restaurantId,
        @Param("date") LocalDate date
    );

    @Query("""
        select i.severity as severity, count(i) as total
        from AiInsight i
        where i.restaurant.id = :restaurantId
          and i.date = :date
          and i.dismissed = false
        group by i.severity
        """)
    List<AiInsightSeverityCount> countActiveBySeverity(
        @Param("restaurantId") Long restaurantId,
        @Param("date") LocalDate date
    );

    interface AiInsightSeverityCount {
        AiSeverity getSeverity();
        long getTotal();
    }
}
