# Cloud Foundry Spring Boot Application for Tanzu Data Services Validation

A lightweight Spring Boot application designed for Cloud Foundry to validate transactions against various Tanzu data services.

## Supported Data Services

- **MySQL** - Relational database operations (CRUD)
- **PostgreSQL** - Relational database operations (CRUD)
- **Valkey/Redis** - Key-value store operations (GET, SET, DELETE, EXISTS, KEYS)
- **RabbitMQ** - Message queue operations (SEND, RECEIVE, QUEUE INFO)

## Features

- ✅ Support for both standard Tanzu-provisioned services and User Provided Services (CUPS)
- ✅ Multi-tenancy support - can be deployed multiple times across different Cloud Foundry spaces
- ✅ Modern web UI with dedicated tabs for each data service
- ✅ RESTful API endpoints for programmatic access
- ✅ Automatic VCAP_SERVICES parsing for Cloud Foundry service bindings
- ✅ Comprehensive transaction validation for all supported services

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Cloud Foundry CLI (for deployment)
- Tanzu data service instances (MySQL, PostgreSQL, Redis/Valkey, RabbitMQ)

## Building the Application

```bash
mvn clean package
```

This will create a JAR file in the `target/` directory.

## Local Development

You can run the application locally for development:

```bash
mvn spring-boot:run
```

The application will be available at `http://localhost:8080`.

For local development, you can configure data services using environment variables (see `application.yml` for options).

## Cloud Foundry Deployment

### 1. Create Service Instances

```bash
# MySQL
cf create-service p.mysql db-small mysql-service

# PostgreSQL
cf create-service postgresql small postgresql-service

# Redis/Valkey
cf create-service redis small redis-service

# RabbitMQ
cf create-service rabbitmq small rabbitmq-service
```

Or use User Provided Services (CUPS):

```bash
cf create-user-provided-service mysql-cups -p '{"host":"hostname","port":3306,"database":"dbname","username":"user","password":"pass"}'
```

### 2. Update manifest.yml

Edit `manifest.yml` and add your service instances to the `services` section:

```yaml
services:
  - mysql-service
  - postgresql-service
  - redis-service
  - rabbitmq-service
```

### 3. Deploy

```bash
cf push
```

Or with a custom manifest:

```bash
cf push -f manifest.yml
```

### 4. Bind Services

If services are not auto-bound via manifest:

```bash
cf bind-service cf-spring-super mysql-service
cf bind-service cf-spring-super postgresql-service
cf bind-service cf-spring-super redis-service
cf bind-service cf-spring-super rabbitmq-service
cf restart cf-spring-super
```

## Usage

### Web UI

Once deployed, access the web UI at your application URL. The interface provides:

- **MySQL Tab**: Perform READ, WRITE, UPDATE, DELETE operations
- **PostgreSQL Tab**: Perform READ, WRITE, UPDATE, DELETE operations
- **Redis/Valkey Tab**: Perform GET, SET, EXISTS, KEYS, DELETE operations
- **RabbitMQ Tab**: Perform SEND, RECEIVE, QUEUE INFO operations

### REST API

The application exposes REST endpoints for each service:

#### MySQL
```bash
# Read
curl -X POST "http://your-app-url/api/mysql/validate?operation=read" \
  -H "Content-Type: application/json" \
  -d '{"key":"test"}'

# Write
curl -X POST "http://your-app-url/api/mysql/validate?operation=write" \
  -H "Content-Type: application/json" \
  -d '{"key":"test","value":"test_value"}'
```

#### PostgreSQL
```bash
curl -X POST "http://your-app-url/api/postgresql/validate?operation=read" \
  -H "Content-Type: application/json" \
  -d '{"key":"test"}'
```

#### Redis/Valkey
```bash
# Set
curl -X POST "http://your-app-url/api/redis/validate?operation=set" \
  -H "Content-Type: application/json" \
  -d '{"key":"test","value":"test_value","ttl":3600}'

# Get
curl -X POST "http://your-app-url/api/redis/validate?operation=get" \
  -H "Content-Type: application/json" \
  -d '{"key":"test"}'
```

#### RabbitMQ
```bash
# Send
curl -X POST "http://your-app-url/api/rabbitmq/validate?operation=send" \
  -H "Content-Type: application/json" \
  -d '{"exchange":"","routingKey":"test_queue","message":"Hello World"}'

# Receive
curl -X POST "http://your-app-url/api/rabbitmq/validate?operation=receive" \
  -H "Content-Type: application/json" \
  -d '{"queue":"test_queue"}'
```

## Multi-Tenancy

The application is designed to be deployed multiple times across different Cloud Foundry spaces. Each deployment:

- Automatically detects and uses services bound to that specific instance
- Parses VCAP_SERVICES independently for each deployment
- Supports both standard services and CUPS in the same deployment
- Isolates data and operations per deployment instance

## Configuration

The application automatically configures data sources based on VCAP_SERVICES. For standard services, it looks for service labels:

- `mysql` - MySQL service instances
- `postgresql` - PostgreSQL service instances
- `redis` or `valkey` - Redis/Valkey service instances
- `rabbitmq` - RabbitMQ service instances

For User Provided Services (CUPS), configure the service name to match one of the above labels.

## Project Structure

```
cf-spring-super/
├── src/
│   ├── main/
│   │   ├── java/com/vmware/cfspringsuper/
│   │   │   ├── config/          # Configuration classes
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── service/         # Validation services
│   │   │   └── CfSpringSuperApplication.java
│   │   └── resources/
│   │       ├── templates/       # Thymeleaf templates
│   │       └── application.yml  # Application configuration
│   └── test/
├── manifest.yml                 # Cloud Foundry deployment manifest
├── pom.xml                     # Maven configuration
└── README.md
```

## Troubleshooting

### Service Not Found

If you see "service not configured" errors:

1. Verify services are bound: `cf services`
2. Check VCAP_SERVICES: `cf env cf-spring-super | grep VCAP_SERVICES`
3. Ensure service labels match expected names (mysql, postgresql, redis/valkey, rabbitmq)

### Connection Errors

- Verify service instances are running: `cf service mysql-service`
- Check network connectivity from your app instance
- Review application logs: `cf logs cf-spring-super --recent`

### UI Not Loading

- Ensure the application is running: `cf app cf-spring-super`
- Check application logs for errors
- Verify the application is accessible: `cf apps`

## License

This project is provided as-is for validation and testing purposes.
