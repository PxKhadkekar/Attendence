# Deployment & Operations Specification

## 1. Environment Architecture

```
+-----------------------------------------------------------------------------------------+
|                                    DOCKER COMPOSE                                       |
|                                                                                         |
|  +--------------------+         +--------------------+         +---------------------+  |
|  |     Frontend       |         |      Backend       |         |      Database       |  |
|  |  (Nginx Alpine)    |-------->|   (Spring Boot 3)  |-------->|      (MySQL 8)      |  |
|  |   Port: 80 / 443   | Reverse |     Port: 8080     |  JDBC   |     Port: 3306      |  |
|  +--------------------+ Proxy   +--------------------+         +---------------------+  |
+-----------------------------------------------------------------------------------------+
```

---

## 2. Configuration & Profiles

### 2.1 Spring Boot Profiles
1. **`prod`**:
   - Strictly disables any mock services or mock BLE authenticators.
   - Requires valid MySQL credentials, strong JWT signing secret, and Flyway enabled.
   - Enforces HTTPS and secure cookies.
2. **`dev` / `test`**:
   - Enables H2 or local MySQL with Testcontainers.
   - Allows Mock BLE scanner testing and seed data generation.

---

## 3. Environment Variables Reference (`.env.example`)

```bash
# ==============================================================================
# MySQL Database Configuration
# ==============================================================================
DB_HOST=mysql
DB_PORT=3306
DB_NAME=placement_attendance
DB_USER=tp_admin
DB_PASSWORD=SecurePassword_ChangeMeInProd!
DB_ROOT_PASSWORD=RootPassword_ChangeMeInProd!

# ==============================================================================
# Spring Boot Backend Configuration
# ==============================================================================
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
JWT_SECRET=4c87a552e6fbbd182069279a08e1a120013b564e9a31828753d085942f741584
JWT_EXPIRATION_MS=86400000
JWT_REFRESH_EXPIRATION_MS=604800000

# File Upload / POI Limits
MAX_FILE_SIZE_BYTES=5242880
MAX_ROW_COUNT=5000

# ==============================================================================
# Frontend Web App Configuration
# ==============================================================================
VITE_API_BASE_URL=/api/v1
PORT=80
```
