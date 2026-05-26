package com.restaurantplanner.notification.service;

public interface NotificationProvider {

    NotificationProviderResult sendSms(NotificationSendCommand command);
}
