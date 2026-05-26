package com.restaurantplanner.ai.api;

import com.restaurantplanner.ai.service.AiService;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/ai/insights")
public class AiController {

    private final AiService aiService;
    private final AiInsightMapper aiInsightMapper;

    public AiController(AiService aiService, AiInsightMapper aiInsightMapper) {
        this.aiService = aiService;
        this.aiInsightMapper = aiInsightMapper;
    }

    @GetMapping
    public List<AiInsightResponse> findByDate(
        @PathVariable Long restaurantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Authentication authentication
    ) {
        return aiService.findByRestaurantIdAndDate(
                restaurantId,
                date,
                (AuthenticatedUser) authentication.getPrincipal()
            )
            .stream()
            .map(aiInsightMapper::toResponse)
            .toList();
    }

    @GetMapping("/summary")
    public Map<String, Long> summary(
        @PathVariable Long restaurantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Authentication authentication
    ) {
        return aiService.summaryBySeverity(restaurantId, date, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PatchMapping("/{insightId}/dismiss")
    public AiInsightResponse dismiss(
        @PathVariable Long restaurantId,
        @PathVariable Long insightId,
        Authentication authentication
    ) {
        return aiInsightMapper.toResponse(
            aiService.dismissInsight(restaurantId, insightId, (AuthenticatedUser) authentication.getPrincipal())
        );
    }
}
