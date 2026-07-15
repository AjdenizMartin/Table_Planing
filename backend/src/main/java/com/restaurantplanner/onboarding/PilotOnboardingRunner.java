package com.restaurantplanner.onboarding;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("onboarding")
public class PilotOnboardingRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PilotOnboardingRunner.class);

    private final PilotOnboardingService onboardingService;
    private final ConfigurableApplicationContext applicationContext;
    private final String manifestPath;

    public PilotOnboardingRunner(
        PilotOnboardingService onboardingService,
        ConfigurableApplicationContext applicationContext,
        @Value("${app.onboarding.manifest:}") String manifestPath
    ) {
        this.onboardingService = onboardingService;
        this.applicationContext = applicationContext;
        this.manifestPath = manifestPath;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (manifestPath.isBlank()) {
            throw new IllegalArgumentException("APP_ONBOARDING_MANIFEST is required for the onboarding profile");
        }

        PilotOnboardingService.PilotOnboardingResult result = onboardingService.onboard(Path.of(manifestPath));
        log.info(
            "Pilot onboarding completed: restaurantId={}, restaurantCreated={}, createdUsers={}, verifiedUsers={}",
            result.restaurantId(),
            result.restaurantCreated(),
            result.createdUsers(),
            result.verifiedUsers()
        );

        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(exitCode);
    }
}
