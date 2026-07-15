TRUNCATE TABLE
    reservation_assignment,
    table_combination_item,
    audit_log,
    notification,
    scheduled_notification,
    notification_log,
    ai_insight,
    storage_resource,
    restaurant_rule,
    refresh_token,
    reservation,
    table_combination,
    restaurant_table,
    customer,
    dining_room,
    role_assignment,
    app_user,
    restaurant
CASCADE;
TRUNCATE TABLE reservation_assignment_resource RESTART IDENTITY CASCADE;
TRUNCATE TABLE table_combination_resource_requirement RESTART IDENTITY CASCADE;
