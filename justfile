# Default target: list all available recipes
default:
    @just --list

# Build all Docker images
build:
    docker compose build

# Compile and install all services cleanly
compile:
    @echo "Compiling common shared library..."
    mvn clean install -f common/pom.xml -DskipTests -B
    @echo "Compiling all microservices..."
    mvn clean compile -f eureka-server/pom.xml -DskipTests -B
    mvn clean compile -f api-gateway/pom.xml -DskipTests -B
    mvn clean compile -f auth-service/pom.xml -DskipTests -B
    mvn clean compile -f user-service/pom.xml -DskipTests -B
    mvn clean compile -f product-service/pom.xml -DskipTests -B
    mvn clean compile -f order-service/pom.xml -DskipTests -B
    mvn clean compile -f payment-service/pom.xml -DskipTests -B
    mvn clean compile -f file-storage-service/pom.xml -DskipTests -B
    mvn clean compile -f notification-service/pom.xml -DskipTests -B
    @echo "✅ All microservices compiled successfully!"

# Start all services
up:
    docker compose up -d

# Start with development overrides (hot reload + debug)
dev:
    docker compose -f docker-compose.yml -f docker-compose.override.yml up -d

# Start production configuration
prod:
    docker compose --profile production up -d

# Stop all services
down:
    docker compose down

# Show logs for all services
logs:
    docker compose logs -f

# Show logs for a specific service (example: just logs-svc auth-service)
logs-svc service:
    docker compose logs -f {{service}}

# Remove all containers and volumes (⚠️ This deletes all data!)
clean:
    docker compose down -v
    docker system prune -f

# Restart all services
restart: down up

# Show status of all services
status:
    docker compose ps

# Quick health check of all microservices
health:
    @echo "Checking service health..."
    @curl -s http://localhost:8761/actuator/health > /dev/null && echo "✅ Eureka Server: Healthy" || echo "❌ Eureka Server: Unhealthy"
    @curl -s http://localhost:8085/actuator/health > /dev/null && echo "✅ Auth Service: Healthy" || echo "❌ Auth Service: Unhealthy"
    @curl -s http://localhost:8084/actuator/health > /dev/null && echo "✅ User Service: Healthy" || echo "❌ User Service: Unhealthy"
    @curl -s http://localhost:8082/actuator/health > /dev/null && echo "✅ Product Service: Healthy" || echo "❌ Product Service: Unhealthy"
    @curl -s http://localhost:8083/actuator/health > /dev/null && echo "✅ Order Service: Healthy" || echo "❌ Order Service: Unhealthy"
    @curl -s http://localhost:8081/actuator/health > /dev/null && echo "✅ Payment Service: Healthy" || echo "❌ Payment Service: Unhealthy"
    @curl -s http://localhost:8086/actuator/health > /dev/null && echo "✅ Notification Service: Healthy" || echo "❌ Notification Service: Unhealthy"
    @curl -s http://localhost:8087/actuator/health > /dev/null && echo "✅ File Storage Service: Healthy" || echo "❌ File Storage Service: Unhealthy"
    @curl -s http://localhost:8080/actuator/health > /dev/null && echo "✅ API Gateway: Healthy" || echo "❌ API Gateway: Unhealthy"

# Access URLs of all registered services
access:
    @echo "Service URLs:"
    @echo "🌐 API Gateway:             http://localhost:8080"
    @echo "🔍 Eureka Dashboard:         http://localhost:8761"
    @echo "📚 Swagger UI (Gateway):     http://localhost:8080/swagger-ui.html"
    @echo "🔐 Auth Service:             http://localhost:8085/swagger-ui.html"
    @echo "👤 User Service:             http://localhost:8084/swagger-ui.html"
    @echo "📦 Product Service:          http://localhost:8082/swagger-ui.html"
    @echo "🛒 Order Service:            http://localhost:8083/swagger-ui.html"
    @echo "💳 Payment Service:          http://localhost:8081/swagger-ui.html"
    @echo "🔔 Notification Service:     http://localhost:8086/swagger-ui.html"
    @echo "📁 File Storage Service:     http://localhost:8087/swagger-ui.html"
    @echo "🐰 RabbitMQ Management:      http://localhost:15672 (admin/password)"

# Print database connection instructions
db-connect:
    @echo "Database Connection Commands:"
    @echo "👤 User DB:         docker exec -it user-db psql -U postgres -d user_service"
    @echo "📦 Product DB:      docker exec -it product-db psql -U postgres -d product_service"
    @echo "🛒 Order DB:        docker exec -it order-db psql -U postgres -d order_service"
    @echo "💳 Payment DB:      docker exec -it payment-db psql -U postgres -d payment_service"
    @echo "🔔 Notification DB:  docker exec -it notification-db psql -U postgres -d notification_service"
    @echo "📁 File Storage DB:  docker exec -it file-storage-db psql -U postgres -d file_storage_service"

# Run quick REST integration tests
test:
    @echo "Running quick integration test..."
    @curl -s http://localhost:8085/auth/register -H "Content-Type: application/json" -d '{"username":"testuser","password":"testpass","email":"test@example.com"}' | jq . || echo "Auth service test failed"
    @curl -s http://localhost:8082/products | jq .[0].name || echo "Product service test failed"
