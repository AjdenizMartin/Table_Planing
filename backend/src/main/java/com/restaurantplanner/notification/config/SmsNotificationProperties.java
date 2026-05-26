package com.restaurantplanner.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.sms")
public class SmsNotificationProperties {

    private boolean enabled = true;
    private String provider = "fake";
    private String twilioAccountSid;
    private String twilioAuthToken;
    private String twilioFromNumber;
    private boolean fakeFailAll = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getTwilioAccountSid() {
        return twilioAccountSid;
    }

    public void setTwilioAccountSid(String twilioAccountSid) {
        this.twilioAccountSid = twilioAccountSid;
    }

    public String getTwilioAuthToken() {
        return twilioAuthToken;
    }

    public void setTwilioAuthToken(String twilioAuthToken) {
        this.twilioAuthToken = twilioAuthToken;
    }

    public String getTwilioFromNumber() {
        return twilioFromNumber;
    }

    public void setTwilioFromNumber(String twilioFromNumber) {
        this.twilioFromNumber = twilioFromNumber;
    }

    public boolean isFakeFailAll() {
        return fakeFailAll;
    }

    public void setFakeFailAll(boolean fakeFailAll) {
        this.fakeFailAll = fakeFailAll;
    }
}
