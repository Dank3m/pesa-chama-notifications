# Notification Service

A microservice for handling notifications (SMS, Email) for the Table Banking Loan Management System.

## Features

- **Kafka Consumer**: Listens to events from the main application
- **SMS Integration**: Infobip SMS gateway integration
- **Email Service**: SMTP email sending with Thymeleaf templates
- **Notification Templates**: Configurable message templates
- **Delivery Tracking**: Track SMS delivery status via webhooks
- **Rate Limiting**: Prevent SMS spam with Redis-based rate limiting
- **Retry Mechanism**: Automatic retry for failed notifications
- **Deduplication**: Prevent duplicate notifications using Redis

## Architecture

```
┌─────────────────┐     ┌───────────┐     ┌─────────────────────┐
│ Table Banking   │────▶│   Kafka   │────▶│ Notification Service│
│   Main App      │     │           │     │                     │
└─────────────────┘     └───────────┘     └──────────┬──────────┘
                                                     │
                              ┌──────────────────────┼──────────────────────┐
                              │                      │                      │
                              ▼                      ▼                      ▼
                        ┌──────────┐          ┌──────────┐          ┌──────────┐
                        │ Infobip  │          │   SMTP   │          │ Firebase │
                        │   SMS    │          │  Email   │          │   Push   │
                        └──────────┘          └──────────┘          └──────────┘
```

## Kafka Topics

The service listens to the following topics:

| Topic | Description |
|-------|-------------|
| `notification-events` | Generic notification events |
| `contribution-events` | Contribution-related events |
| `loan-events` | Loan-related events |

## Event Types

### Contribution Events
- `CONTRIBUTION_REMINDER` - Reminder before due date
- `CONTRIBUTION_RECEIVED` - Confirmation of payment
- `CONTRIBUTION_OVERDUE` - Alert for missed payment
- `CONTRIBUTION_DEFAULTED` - Default notification

### Loan Events
- `LOAN_APPROVED` - Loan approval notification
- `LOAN_DISBURSED` - Disbursement confirmation
- `LOAN_PAYMENT_REMINDER` - Payment reminder
- `LOAN_PAYMENT_RECEIVED` - Payment confirmation
- `LOAN_OVERDUE` - Overdue alert
- `LOAN_CREATED_FROM_DEFAULT` - Default conversion notice

## Configuration

### Environment Variables

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=notifications
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# SMS (Infobip)
SMS_ENABLED=true
INFOBIP_BASE_URL=https://api.infobip.com
INFOBIP_API_KEY=your-api-key
INFOBIP_SENDER_ID=TableBank

# Email
EMAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password
EMAIL_FROM=noreply@tablebanking.com
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/notifications/send` | Send notification manually |
| GET | `/api/v1/notifications/{id}` | Get notification by ID |
| GET | `/api/v1/notifications/member/{memberId}` | Get member's notifications |
| GET | `/api/v1/notifications/stats` | Get notification statistics |
| POST | `/api/v1/notifications/delivery-webhook/infobip` | Infobip webhook |
| GET | `/api/v1/notifications/delivery-status/{messageId}` | Check delivery status |

## Running Locally

### Prerequisites
- Java 21
- Docker & Docker Compose
- Maven

### Start Dependencies

```bash
docker-compose up -d postgres-notifications redis kafka
```

### Run the Service

```bash
mvn spring-boot:run
```

### Or use Docker

```bash
docker-compose up -d notification-service
```

## Integration with Main Application

### Update Main App Events

The main application needs to include contact details in events:

```java
// In LoanService.java - publishLoanEvent method
LoanEvent event = LoanEvent.builder()
    .eventId(UUID.randomUUID().toString())
    .eventType(eventType)
    .loanId(loan.getId())
    .memberId(loan.getMember().getId())
    .memberName(loan.getMember().getFirstName() + " " + loan.getMember().getLastName())
    .phoneNumber(loan.getMember().getPhoneNumber())  // ADD THIS
    .email(loan.getMember().getEmail())              // ADD THIS
    .groupId(loan.getMember().getGroup().getId())
    .groupName(loan.getMember().getGroup().getName()) // ADD THIS
    .amount(loan.getPrincipalAmount())
    .outstandingBalance(loan.getOutstandingBalance())
    .status(loan.getStatus().name())
    .timestamp(Instant.now())
    .build();
```

## Notification Templates

Templates are stored in the database and can be customized:

```sql
UPDATE notification_templates 
SET body_template = 'Your custom message {memberName}...' 
WHERE template_code = 'CONTRIBUTION_REMINDER';
```

### Available Variables

| Variable | Description |
|----------|-------------|
| `{memberName}` | Member's full name |
| `{groupName}` | Banking group name |
| `{amount}` | Amount (contribution/loan) |
| `{month}` | Cycle month |
| `{dueDate}` | Due date |
| `{outstanding}` | Outstanding balance |
| `{loanNumber}` | Loan reference number |

## Health Check

```bash
curl http://localhost:8081/actuator/health
```

## Swagger UI

Access API documentation at: http://localhost:8081/swagger-ui.html

## License

MIT
