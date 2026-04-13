# Loop DFS POS Integration Service

A Spring Boot application that:
1. Exposes a REST API to receive a country name
2. Calls a SOAP endpoint to retrieve the ISO code
3. Calls a second SOAP endpoint to retrieve full country information
4. Persists data in MySQL and exposes full CRUD REST APIs
5. Is deployable on Kubernetes with zero-downtime rolling updates

---

## System Design

```
Client
  │  POST /api/v1/countries  {"name": "kenya"}
  ▼
CountryController  (REST layer)
  │  toSentenceCase("kenya") → "Kenya"
  ▼
CountryInfoService  (business logic)
  │  1. call SOAP CountryISOCode("Kenya")  → "KE"
  │  2. call SOAP FullCountryInfo("KE")    → XML
  │  3. parse XML → CountryInfo + Languages
  │  4. save to MySQL
  ▼
MySQL  (persistence)
  └── country_info table
  └── languages table
```

**Key design decisions:**
- **MVC separation**: Controller → Service → Repository.
- **Retry with exponential backoff** (Spring Retry) on all SOAP calls.
- **Circuit breaker** via `@Recover` – graceful fallback after 3 failed attempts.
- **Stateless pods** – session state in DB, safe to scale horizontally.
- **Structured logging** with MDC context (timestamp, thread, level, logger).
- **Actuator + Prometheus** metrics for observability.
- **Idempotent save** – if ISO code already exists in DB, returns cached result.

---

## Prerequisites (Local Run)

- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for MySQL)
- (Optional) SoapUI 5.x for manual SOAP testing

---

## Running Locally

### 1. Start MySQL with Docker Compose

```bash
# Start MySQL (creates pos_integration_db automatically)
docker run -d \
  --name pos-mysql \
  -e MYSQL_ROOT_PASSWORD=password \
  -e MYSQL_DATABASE=pos_integration_db \
  -e MYSQL_USER=posuser \
  -e MYSQL_PASSWORD=password \
  -p 3307:3307 \
  mysql:8.0

# Wait ~15s, then verify
docker exec -it pos-mysql mysql -uroot -ppassword -e "SHOW DATABASES;"
```

### 2. Build the Application

```bash
mvn clean package -DskipTests
```

### 3. Run the Application

```bash
java -jar target/pos-integration-1.0.0.jar \
  --spring.datasource.url=jdbc:mysql://localhost:3307/pos_integration_db?useSSL=false\&allowPublicKeyRetrieval=true \
  --spring.datasource.username=root \
  --spring.datasource.password=password
```

The application starts on **port 8080**.

---

## Testing the API

### Health Check
```bash
curl http://localhost:8080/actuator/health
# {"status":"UP",...}
```

### POST – Fetch and Save Country
```bash
curl -X POST http://localhost:8080/api/v1/countries \
  -H "Content-Type: application/json" \
  -d '{"name": "kenya"}'
```
Expected response:
```json
{
  "success": true,
  "message": "Country information fetched and saved successfully.",
  "data": {
    "id": 1,
    "name": "Kenya",
    "isoCode": "KE",
    "capitalCity": "Nairobi",
    "phoneCode": "254",
    "continentCode": "AF",
    "currencyIsoCode": "KES",
    "countryFlag": "http://www.dorsprong.org/WebSamples.CountryInfo/Flags/Kenya.bmp",
    "languages": [
      { "id": 1, "isoCode": "swa", "name": "Swahili" },
      { "id": 2, "isoCode": "eng", "name": "English" }
    ]
  }
}
```

### GET – All Countries
```bash
curl http://localhost:8080/api/v1/countries
```

### GET – Country by ID
```bash
curl http://localhost:8080/api/v1/countries/1
```

### PUT – Update Country
```bash
curl -X PUT http://localhost:8080/api/v1/countries/1 \
  -H "Content-Type: application/json" \
  -d '{"capitalCity": "Nairobi", "phoneCode": "+254"}'
```

### DELETE – Remove Country
```bash
curl -X DELETE http://localhost:8080/api/v1/countries/1
# {"success":true,"message":"Country deleted successfully."}
```

### Error Handling Examples
```bash
# Country not found
curl http://localhost:8080/api/v1/countries/9999
# HTTP 404 {"success":false,"message":"Country not found with id: 9999"}

# Blank name validation
curl -X POST http://localhost:8080/api/v1/countries \
  -H "Content-Type: application/json" \
  -d '{"name": ""}'
# HTTP 400 {"success":false,"message":"Validation failed","errors":{"name":"Country name must not be blank"}}
```

---

## SoapUI Manual Testing

1. Open SoapUI → File → New SOAP Project
2. Set WSDL URL: `http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso?WSDL`
3. Click OK – you should see all operations listed
4. Test `CountryISOCode`: set `<web:sCountryName>Kenya</web:sCountryName>`, run → should return `KE`
5. Test `FullCountryInfo`: set `<web:sCountryISOCode>KE</web:sCountryISOCode>`, run → returns full XML

---

## Project Structure

```
pos-integration/
├── src/main/java/com/ncba/pos/
│   ├── PosIntegrationApplication.java     # Entry point
│   ├── controller/
│   │   └── CountryController.java         # REST endpoints
│   ├── service/
│   │   └── CountryInfoService.java        # Business logic
│   ├── client/
│   │   └── CountryInfoSoapClient.java     # SOAP integration
│   ├── model/
│   │   ├── CountryInfo.java               # JPA entity
│   │   └── Language.java                  # JPA entity
│   ├── repository/
│   │   ├── CountryInfoRepository.java
│   │   └── LanguageRepository.java
│   ├── dto/
│   │   └── CountryDtos.java               # Request/Response DTOs
│   ├── config/
│   │   └── SoapConfig.java                # SOAP WebServiceTemplate
│   └── exception/
│       ├── CountryNotFoundException.java
│       ├── SoapIntegrationException.java
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   └── application.properties
├── k8s/
│   ├── 00-namespace.yaml
│   ├── 01-configmap.yaml
│   ├── 02-secret.yaml
│   ├── 03-mysql.yaml
│   ├── 04-deployment.yaml
│   ├── 05-service-hpa.yaml
│   └── 06-ingress.yaml
├── docs/
│   ├── DEPLOYMENT_GUIDE.md
│   └── TROUBLESHOOTING_GUIDE.md
├── Dockerfile
├── deploy.sh
└── pom.xml
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:mysql://localhost:3307/pos_integration_db...` | JDBC URL |
| `DB_USERNAME` | `root` | DB username |
| `DB_PASSWORD` | `password` | DB password |
| `SERVER_PORT` | `8080` | HTTP server port |
| `SOAP_ENDPOINT_URL` | oorsprong.org URL | SOAP service endpoint |

---

## Additional Documentation

- [Kubernetes Deployment Guide](docs/DEPLOYMENT_GUIDE.md)
- [Kubernetes Troubleshooting Guide](docs/TROUBLESHOOTING_GUIDE.md)
