# RaksMule API Test Guide

## Quick Reference

**Base URL**: `https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io` (or `http://localhost:8082` for local)
**Authentication**: Basic Authentication
**Credentials**: `raks` / `admin`

---

## REST API

### 1. Create Order (POST)

**Endpoint**: `POST https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/api/orders`

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
  "message": "Order created successfully",
  "timestamp": "2026-01-23T23:06:52.428-06:00"
}
```

**PowerShell Test**:
```powershell
$headers = @{
    "Content-Type" = "application/json"
    "Authorization" = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("raks:admin"))
}
$body = '{"customerName":"John Doe","customerEmail":"john.doe@example.com","orderDate":"2026-01-18","productName":"Widget Pro","quantity":5,"totalAmount":249.99}'
Invoke-WebRequest -Uri "https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/api/orders" -Method POST -Headers $headers -Body $body
```

**cURL Test**:
```bash
curl -X POST https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/api/orders \
  -u raks:admin \
  -H "Content-Type: application/json" \
  -d '{"customerName":"John Doe","customerEmail":"john.doe@example.com","orderDate":"2026-01-18","productName":"Widget Pro","quantity":5,"totalAmount":249.99}'
```

---

### 2. Get Order (GET)

**Endpoint**: `GET https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/api/orders/{orderNumber}`

**Example**: `GET https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/api/orders/ORD-20260118-001`

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
  "status": "CONFIRMED",
  "timestamp": "2026-01-23T23:06:52.428-06:00"
}
```

**PowerShell Test**:
```powershell
$headers = @{
    "Authorization" = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("raks:admin"))
}
Invoke-WebRequest -Uri "https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/api/orders/ORD-20260118-001" -Method GET -Headers $headers
```

**cURL Test**:
```bash
curl -u raks:admin https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/api/orders/ORD-20260118-001
```

---

## SOAP API

### WSDL

**Endpoint**: `GET https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/soap/orderservice?wsdl`

**PowerShell Test**:
```powershell
Invoke-WebRequest -Uri "https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/soap/orderservice?wsdl" -Method GET
```

---

### 1. CreateOrder Operation

**Endpoint**: `POST https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/soap/orderservice`

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
      <timestamp>2026-01-23T23:06:52.428-06:00</timestamp>
    </tns:CreateOrderResponse>
  </soap:Body>
</soap:Envelope>
```

**PowerShell Test**:
```powershell
$headers = @{
    "Content-Type" = "text/xml"
    "SOAPAction" = "http://raks.com/orderservice/CreateOrder"
    "Authorization" = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("raks:admin"))
}
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
Invoke-WebRequest -Uri "https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/soap/orderservice" -Method POST -Headers $headers -Body $body
```

**cURL Test**:
```bash
curl -X POST https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/soap/orderservice \
  -u raks:admin \
  -H "Content-Type: text/xml" \
  -H "SOAPAction: http://raks.com/orderservice/CreateOrder" \
  -d '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="http://raks.com/orderservice"><soap:Body><tns:CreateOrderRequest><customerName>John Doe</customerName><customerEmail>john.doe@example.com</customerEmail><orderDate>2026-01-18</orderDate><productName>Widget Pro</productName><quantity>5</quantity><totalAmount>249.99</totalAmount></tns:CreateOrderRequest></soap:Body></soap:Envelope>'
```

---

### 2. GetOrder Operation

**Endpoint**: `POST https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/soap/orderservice`

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
      <timestamp>2026-01-23T23:06:52.428-06:00</timestamp>
    </tns:GetOrderResponse>
  </soap:Body>
</soap:Envelope>
```

**PowerShell Test**:
```powershell
$headers = @{
    "Content-Type" = "text/xml"
    "SOAPAction" = "http://raks.com/orderservice/GetOrder"
    "Authorization" = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("raks:admin"))
}
$body = @"
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tns="http://raks.com/orderservice">
  <soap:Body>
    <tns:GetOrderRequest>
      <orderNumber>ORD-20260118-001</orderNumber>
    </tns:GetOrderRequest>
  </soap:Body>
</soap:Envelope>
"@
Invoke-WebRequest -Uri "https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/soap/orderservice" -Method POST -Headers $headers -Body $body
```

**cURL Test**:
```bash
curl -X POST https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/soap/orderservice \
  -u raks:admin \
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
- **Basic Authentication required** (user: `raks`, password: `admin`)
- Responses are static/mock data
- SOAP operations require proper XML envelope structure
- SOAP operations are distinguished by the request body content (CreateOrderRequest vs GetOrderRequest)
