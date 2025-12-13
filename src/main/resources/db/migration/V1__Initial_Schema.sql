-- V1__Initial_Schema.sql
-- Notification Service Database Schema

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Notification Templates
CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    template_code VARCHAR(50) NOT NULL UNIQUE,
    template_name VARCHAR(100) NOT NULL,
    description TEXT,
    channel VARCHAR(20) NOT NULL,  -- SMS, EMAIL, PUSH
    subject_template TEXT,  -- For email
    body_template TEXT NOT NULL,
    variables JSONB,  -- List of expected variables
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_channel CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'ALL'))
);

-- Notification Log
CREATE TABLE notification_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id VARCHAR(100),  -- Original event ID from Kafka
    event_type VARCHAR(50) NOT NULL,
    member_id UUID NOT NULL,
    member_name VARCHAR(100),
    group_id UUID,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(255) NOT NULL,  -- Phone number or email
    template_code VARCHAR(50),
    subject TEXT,
    message TEXT NOT NULL,
    variables JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider VARCHAR(50),  -- infobip, smtp, firebase
    provider_message_id VARCHAR(255),
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_notification_channel CHECK (channel IN ('SMS', 'EMAIL', 'PUSH')),
    CONSTRAINT valid_notification_status CHECK (status IN ('PENDING', 'QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'CANCELLED', 'READ'))
);

-- Delivery Status Updates (from webhooks)
CREATE TABLE delivery_status_updates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    notification_id UUID NOT NULL REFERENCES notification_logs(id),
    provider_message_id VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    status_description TEXT,
    raw_response JSONB,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Notification Preferences (per member)
CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    member_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_preference UNIQUE (member_id, channel, event_type),
    CONSTRAINT valid_pref_channel CHECK (channel IN ('SMS', 'EMAIL', 'PUSH', 'ALL'))
);

-- Scheduled Notifications (for future sending)
CREATE TABLE scheduled_notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    notification_log_id UUID REFERENCES notification_logs(id),
    scheduled_time TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_schedule_status CHECK (status IN ('PENDING', 'PROCESSED', 'CANCELLED'))
);

-- Rate Limiting Tracking
CREATE TABLE rate_limit_tracking (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    recipient VARCHAR(255) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    message_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_rate_window UNIQUE (recipient, channel, window_start)
);

-- Indexes
CREATE INDEX idx_notification_logs_member ON notification_logs(member_id);
CREATE INDEX idx_notification_logs_event_id ON notification_logs(event_id);
CREATE INDEX idx_notification_logs_status ON notification_logs(status);
CREATE INDEX idx_notification_logs_channel ON notification_logs(channel);
CREATE INDEX idx_notification_logs_created ON notification_logs(created_at);
CREATE INDEX idx_notification_logs_scheduled ON notification_logs(scheduled_at) WHERE scheduled_at IS NOT NULL;

CREATE INDEX idx_delivery_status_notification ON delivery_status_updates(notification_id);
CREATE INDEX idx_delivery_status_provider_msg ON delivery_status_updates(provider_message_id);

CREATE INDEX idx_preferences_member ON notification_preferences(member_id);

CREATE INDEX idx_scheduled_status ON scheduled_notifications(status, scheduled_time);

CREATE INDEX idx_rate_limit_recipient ON rate_limit_tracking(recipient, channel, window_start);

-- Trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_notification_templates_updated_at 
    BEFORE UPDATE ON notification_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notification_logs_updated_at 
    BEFORE UPDATE ON notification_logs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_notification_preferences_updated_at 
    BEFORE UPDATE ON notification_preferences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert default templates
INSERT INTO notification_templates (template_code, template_name, description, channel, subject_template, body_template, variables) VALUES
('CONTRIBUTION_REMINDER', 'Contribution Reminder', 'Reminder for upcoming contribution', 'SMS', NULL, 
 'Dear {memberName}, your monthly contribution of KES {amount} for {month} is due by {dueDate}. Please make payment to avoid penalties. - {groupName}',
 '["memberName", "amount", "month", "dueDate", "groupName"]'),

('CONTRIBUTION_REMINDER_EMAIL', 'Contribution Reminder Email', 'Email reminder for upcoming contribution', 'EMAIL', 
 'Contribution Reminder - {month}',
 'Dear {memberName},\n\nThis is a reminder that your monthly contribution of KES {amount} for {month} is due by {dueDate}.\n\nPlease make your payment to avoid any penalties.\n\nThank you,\n{groupName}',
 '["memberName", "amount", "month", "dueDate", "groupName"]'),

('CONTRIBUTION_RECEIVED', 'Contribution Received', 'Confirmation of contribution received', 'SMS', NULL,
 'Dear {memberName}, we have received your contribution of KES {amount} for {month}. Thank you! - {groupName}',
 '["memberName", "amount", "month", "groupName"]'),

('CONTRIBUTION_OVERDUE', 'Contribution Overdue', 'Alert for overdue contribution', 'SMS', NULL,
 'Dear {memberName}, your contribution of KES {amount} for {month} is overdue. Please pay immediately to avoid conversion to loan. - {groupName}',
 '["memberName", "amount", "month", "groupName"]'),

('LOAN_APPROVED', 'Loan Approved', 'Notification when loan is approved', 'SMS', NULL,
 'Dear {memberName}, your loan of KES {amount} has been approved. It will be disbursed within 24 hours. - {groupName}',
 '["memberName", "amount", "groupName"]'),

('LOAN_DISBURSED', 'Loan Disbursed', 'Notification when loan is disbursed', 'SMS', NULL,
 'Dear {memberName}, KES {amount} has been disbursed to your account. Repayment starts {startDate}. Outstanding: KES {outstanding}. - {groupName}',
 '["memberName", "amount", "startDate", "outstanding", "groupName"]'),

('LOAN_PAYMENT_REMINDER', 'Loan Payment Reminder', 'Reminder for loan payment', 'SMS', NULL,
 'Dear {memberName}, your loan payment of KES {amount} is due on {dueDate}. Outstanding balance: KES {outstanding}. - {groupName}',
 '["memberName", "amount", "dueDate", "outstanding", "groupName"]'),

('LOAN_PAYMENT_RECEIVED', 'Loan Payment Received', 'Confirmation of loan payment', 'SMS', NULL,
 'Dear {memberName}, we received your loan payment of KES {amount}. Outstanding balance: KES {outstanding}. Thank you! - {groupName}',
 '["memberName", "amount", "outstanding", "groupName"]'),

('LOAN_OVERDUE', 'Loan Overdue', 'Alert for overdue loan payment', 'SMS', NULL,
 'URGENT: Dear {memberName}, your loan payment is overdue. Outstanding: KES {outstanding}. Please pay immediately to avoid penalties. - {groupName}',
 '["memberName", "outstanding", "groupName"]'),

('DEFAULT_CONVERTED', 'Default Converted to Loan', 'Notification when contribution default is converted to loan', 'SMS', NULL,
 'Dear {memberName}, your unpaid contribution of KES {amount} has been converted to a loan with 10% monthly interest. - {groupName}',
 '["memberName", "amount", "groupName"]');
