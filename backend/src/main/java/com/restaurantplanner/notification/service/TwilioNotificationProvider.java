package com.restaurantplanner.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.notification.config.SmsNotificationProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "app.notification.sms", name = "provider", havingValue = "twilio")
public class TwilioNotificationProvider implements NotificationProvider {

    private final RestClient restClient;
    private final SmsNotificationProperties properties;
    private final ObjectMapper objectMapper;

    public TwilioNotificationProvider(
        RestClient.Builder restClientBuilder,
        SmsNotificationProperties properties,
        ObjectMapper objectMapper
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public NotificationProviderResult sendSms(NotificationSendCommand command) {
        validateConfiguration();

        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", command.recipientPhone());
        form.add("From", properties.getTwilioFromNumber());
        form.add("Body", command.messageBody());

        String authorization = Base64.getEncoder().encodeToString(
            (properties.getTwilioAccountSid() + ":" + properties.getTwilioAuthToken())
                .getBytes(StandardCharsets.UTF_8)
        );

        String response = restClient.post()
            .uri("https://api.twilio.com/2010-04-01/Accounts/{accountSid}/Messages.json", properties.getTwilioAccountSid())
            .header(HttpHeaders.AUTHORIZATION, "Basic " + authorization)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(String.class);

        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            String providerMessageId = jsonNode.path("sid").asText(null);
            if (!StringUtils.hasText(providerMessageId)) {
                throw new IllegalStateException("Twilio response did not include sid");
            }
            return new NotificationProviderResult(providerMessageId);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not parse Twilio response", exception);
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getTwilioAccountSid())
            || !StringUtils.hasText(properties.getTwilioAuthToken())
            || !StringUtils.hasText(properties.getTwilioFromNumber())) {
            throw new IllegalStateException("Twilio SMS provider is not fully configured");
        }
    }
}
