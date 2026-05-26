package com.restaurantplanner.notification.service;

import com.restaurantplanner.notification.config.SmsNotificationProperties;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.notification.sms", name = "provider", havingValue = "fake", matchIfMissing = true)
public class FakeNotificationProvider implements NotificationProvider {

    private final SmsNotificationProperties properties;

    public FakeNotificationProvider(SmsNotificationProperties properties) {
        this.properties = properties;
    }

    @Override
    public NotificationProviderResult sendSms(NotificationSendCommand command) {
        String recipientPhone = command.recipientPhone() == null
            ? ""
            : command.recipientPhone().toLowerCase(Locale.ROOT);

        if (properties.isFakeFailAll() || recipientPhone.contains("fail")) {
            throw new IllegalStateException("Fake SMS provider forced failure");
        }

        return new NotificationProviderResult("fake-sms-" + UUID.randomUUID());
    }
}
