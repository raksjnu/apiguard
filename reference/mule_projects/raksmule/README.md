# RaksMule - Reference Mule Project

## Overview
This is a reference Mule 4 application that provides both SOAP and REST implementations of an Order Service. It's designed for testing API comparison tools and demonstrating Mule capabilities.

## Features

### SOAP Service (OrderService)
- **Endpoint**: `http://localhost:8081/soap/orderservice`
- **WSDL**: Available at `http://localhost:8081/soap/orderservice?wsdl`
- **Operations**:
  - `CreateOrder` - Create a new order
  - `GetOrder` - Retrieve order details by order number

### REST API (Order API)
- **Base URI**: `http://localhost:8081/api`
- **RAML**: Located at `src/main/resources/api/order-api.raml`
- **Endpoints**:
  - `POST /api/orders` - Create a new order
  - `GET /api/orders/{orderNumber}` - Get order details

### Authentication
Both SOAP and REST endpoints support **optional** Basic Authentication:
- **Username**: `raks`
- **Password**: `raks`

Authentication is optional - requests without credentials will still be processed.

## Data Model

### Order Fields
- `orderNumber` - Unique order identifier (auto-generated)
- `customerName` - Customer's full name
- `customerEmail` - Customer's email address
- `orderDate` - Date of order
- `productName` - Name of product ordered
- `quantity` - Quantity ordered
- `totalAmount` - Total order amount
- `status` - Order status (SUCCESS, CONFIRMED, etc.)

## Building and Deploying

### Build
```bash
mvn clean package
```

### Deploy to Standalone Mule Runtime
```bash
# Copy the JAR to your Mule apps directory
copy target\raksmule-1.0.0-mule-application.jar C:\raks\mule-enterprise-standalone-4.10.2\apps\
```

### Test Endpoints

#### SOAP CreateOrder (with curl)
```bash
curl -X POST http://localhost:8081/soap/orderservice \
  -H "Content-Type: text/xml" \
  -u raks:raks \
  -d @- << EOF
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="http://raks.com/orderservice">
  <soap:Body>
    <tns:CreateOrderRequest>
      <customerName>John Doe</customerName>
      <customerEmail>john.doe@example.com</customerEmail>
      <orderDate>2026-01-18</orderDate>
      <productName>Widget Pro</productName>
      <quantity>5</quantity>
      <totalAmount>249.99</totalAmount>
    </tns:CreateOrderRequest>
  </soap:Body>
</soap:Envelope>
EOF
```

#### REST Create Order (with curl)
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -u raks:raks \
  -d '{
    "customerName": "John Doe",
    "customerEmail": "john.doe@example.com",
    "orderDate": "2026-01-18",
    "productName": "Widget Pro",
    "quantity": 5,
    "totalAmount": 249.99
  }'
```

#### REST Get Order (with curl)
```bash
curl http://localhost:8081/api/orders/ORD-20260118-001 -u raks:raks
```

## Single JAR Deployment
Yes, both SOAP and REST services are packaged in a **single Mule application JAR**. Mule 4 fully supports multiple API implementations (SOAP, REST, etc.) in one application.

## Use Cases
- Testing API comparison tools (e.g., `apiurlcomparison`)
- Demonstrating SOAP vs REST implementations
- Learning Mule APIKit for both SOAP and REST
- Basic authentication testing

## Technical Details
- **Mule Runtime**: 4.6.2
- **APIKit**: 1.10.4 (REST)
- **SOAP Engine**: 1.6.7 (SOAP)
- **HTTP Connector**: 1.9.3
