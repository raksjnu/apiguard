# RaksMule API Test Guide

## Quick Reference

**Base URL**: `http://localhost:8082`
**Authentication**: None (optional - not implemented)
**Credentials**: N/A (authentication disabled for simplicity)

---

## REST API

### 1. Create Order (POST)

**Endpoint**: `POST http://localhost:8082/api/orders`

**Headers**:
```
Content-Type: application/json
```

**Request Body**:
```json
{
  "customerName": "John Doe",
  "customerEmail": "john.doe@example.com",
  "orderDate": "2026-01-18",
  "productName": "Widget Pro",
  "quantity": 5,
  "totalAmount": 249.99
}
```

**Response** (201 Created):
```json
{
  "orderNumber": "ORD-20260118-001",
  "status": "SUCCESS",
  "message": "Order created successfully"
}
```

**PowerShell Test**:
```powershell
$headers = @{"Content-Type" = "application/json"}
$body = '{"customerName":"John Doe","customerEmail":"john.doe@example.com","orderDate":"2026-01-18","productName":"Widget Pro","quantity":5,"totalAmount":249.99}'
Invoke-WebRequest -Uri "http://localhost:8082/api/orders" -Method POST -Headers $headers -Body $body
```

**cURL Test**:
```bash
curl -X POST http://localhost:8082/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerName":"John Doe","customerEmail":"john.doe@example.com","orderDate":"2026-01-18","productName":"Widget Pro","quantity":5,"totalAmount":249.99}'
```

---

### 2. Get Order (GET)

**Endpoint**: `GET http://localhost:8082/api/orders/{orderNumber}`

**Example**: `GET http://localhost:8082/api/orders/ORD-20260118-001`

**Headers**: None required

**Response** (200 OK):
```json
{
  "orderNumber": "ORD-20260118-001",
  "customerName": "John Doe",
  "customerEmail": "john.doe@example.com",
  "orderDate": "2026-01-18",
  "productName": "Widget Pro",
  "quantity": 5,
  "totalAmount": 249.99,
  "status": "CONFIRMED"
}
```

**PowerShell Test**:
```powershell
Invoke-WebRequest -Uri "http://localhost:8082/api/orders/ORD-20260118-001" -Method GET
```

**cURL Test**:
```bash
curl http://localhost:8082/api/orders/ORD-20260118-001
```

---

## SOAP API

### WSDL

**Endpoint**: `GET http://localhost:8082/soap/orderservice?wsdl`

**PowerShell Test**:
```powershell
Invoke-WebRequest -Uri "http://localhost:8082/soap/orderservice?wsdl" -Method GET
```

---

### 1. CreateOrder Operation

**Endpoint**: `POST http://localhost:8082/soap/orderservice`

**Headers**:
```
Content-Type: text/xml
SOAPAction: http://raks.com/orderservice/CreateOrder
```

**Request Body**:
```xml
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
```

**Response**:
```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="http://raks.com/orderservice">
  <soap:Body>
    <tns:CreateOrderResponse>
      <orderNumber>ORD-20260118-001</orderNumber>
      <status>SUCCESS</status>
      <message>Order created successfully</message>
    </tns:CreateOrderResponse>
  </soap:Body>
</soap:Envelope>
```

**PowerShell Test**:
```powershell
$headers = @{"Content-Type" = "text/xml"; "SOAPAction" = "http://raks.com/orderservice/CreateOrder"}
$body = @"
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
"@
Invoke-WebRequest -Uri "http://localhost:8082/soap/orderservice" -Method POST -Headers $headers -Body $body
```

**cURL Test**:
```bash
curl -X POST http://localhost:8082/soap/orderservice \
  -H "Content-Type: text/xml" \
  -H "SOAPAction: http://raks.com/orderservice/CreateOrder" \
  -d '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="http://raks.com/orderservice"><soap:Body><tns:CreateOrderRequest><customerName>John Doe</customerName><customerEmail>john.doe@example.com</customerEmail><orderDate>2026-01-18</orderDate><productName>Widget Pro</productName><quantity>5</quantity><totalAmount>249.99</totalAmount></tns:CreateOrderRequest></soap:Body></soap:Envelope>'
```

---

### 2. GetOrder Operation

**Endpoint**: `POST http://localhost:8082/soap/orderservice`

**Headers**:
```
Content-Type: text/xml
SOAPAction: http://raks.com/orderservice/GetOrder
```

**Request Body**:
```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="http://raks.com/orderservice">
  <soap:Body>
    <tns:GetOrderRequest>
      <orderNumber>ORD-20260118-001</orderNumber>
    </tns:GetOrderRequest>
  </soap:Body>
</soap:Envelope>
```

**Response**:
```xml
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="http://raks.com/orderservice">
  <soap:Body>
    <tns:GetOrderResponse>
      <orderNumber>ORD-20260118-001</orderNumber>
      <customerName>John Doe</customerName>
      <customerEmail>john.doe@example.com</customerEmail>
      <orderDate>2026-01-18</orderDate>
      <productName>Widget Pro</productName>
      <quantity>5</quantity>
      <totalAmount>249.99</totalAmount>
      <status>CONFIRMED</status>
    </tns:GetOrderResponse>
  </soap:Body>
</soap:Envelope>
```

**PowerShell Test**:
```powershell
$headers = @{"Content-Type" = "text/xml"; "SOAPAction" = "http://raks.com/orderservice/GetOrder"}
$body = @"
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="http://raks.com/orderservice">
  <soap:Body>
    <tns:GetOrderRequest>
      <orderNumber>ORD-20260118-001</orderNumber>
    </tns:GetOrderRequest>
  </soap:Body>
</soap:Envelope>
"@
Invoke-WebRequest -Uri "http://localhost:8082/soap/orderservice" -Method POST -Headers $headers -Body $body
```

**cURL Test**:
```bash
curl -X POST http://localhost:8082/soap/orderservice \
  -H "Content-Type: text/xml" \
  -H "SOAPAction: http://raks.com/orderservice/GetOrder" \
  -d '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="http://raks.com/orderservice"><soap:Body><tns:GetOrderRequest><orderNumber>ORD-20260118-001</orderNumber></tns:GetOrderRequest></soap:Body></soap:Envelope>'
```

---

## Summary Table

| Service | Operation | Method | Endpoint | Content-Type |
|---------|-----------|--------|----------|--------------|
| REST | Create Order | POST | `/api/orders` | application/json |
| REST | Get Order | GET | `/api/orders/{orderNumber}` | - |
| SOAP | CreateOrder | POST | `/soap/orderservice` | text/xml |
| SOAP | GetOrder | POST | `/soap/orderservice` | text/xml |

## Notes

- All endpoints run on port **8082**
- No authentication required (simplified for testing)
- Responses are static/mock data
- SOAP operations require proper XML envelope structure
- SOAP operations are distinguished by the request body content (CreateOrderRequest vs GetOrderRequest)
