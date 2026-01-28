# RaksMule API Test Guide

## Quick Reference

**CloudHub URL**: `https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io`
**CloudHub mTLS URL**: `https://mule-worker-raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io:8082` (Worker Direct)
**Local URL**: `http://localhost:8081` (Standard)
**Local mTLS URL**: `https://localhost:8082` (Secure)

**Standard Authentication**: Basic Authentication (`raks` / `admin`)
**mTLS Authentication**: Mutual TLS with Client Certificate

---

## REST API

### 1. Create Order (POST)

**Endpoint (CloudHub)**: `POST https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/api/orders`
**Endpoint (Local)**: `POST http://localhost:8081/api/orders`

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
Invoke-WebRequest -Uri "http://localhost:8082/api/orders" -Method POST -Headers $headers -Body $body
```

---

### 2. Get Order (GET)

**Endpoint (CloudHub)**: `GET https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/api/orders/{orderNumber}`
**Endpoint (Local)**: `GET http://localhost:8082/api/orders/{orderNumber}`

**Example (Local)**: `GET http://localhost:8082/api/orders/ORD-20260118-001`

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
Invoke-WebRequest -Uri "http://localhost:8082/api/orders/ORD-20260118-001" -Method GET -Headers $headers
```

---

### 3. Large Order Simulation (GET - Bulk)

Use the `count` query parameter to simulate large response payloads.

**Endpoint (Local)**: `GET http://localhost:8081/api/orders?count=100`

**Behavior**:

- Returns an array of random orders.
- If `count=500`, the response size will be ~2MB (useful for performance testing).

**PowerShell Test**:
```powershell
$headers = @{"Authorization" = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("raks:admin"))}
Invoke-WebRequest -Uri "http://localhost:8082/api/orders?count=100" -Method GET -Headers $headers
```

---

## SOAP API

### WSDL

**Endpoint (CloudHub)**: `GET https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/soap/orderservice?wsdl`
**Endpoint (Local)**: `GET http://localhost:8081/soap/orderservice?wsdl`

**PowerShell Test**:
```powershell
Invoke-WebRequest -Uri "http://localhost:8082/soap/orderservice?wsdl" -Method GET
```

---

### 1. CreateOrder Operation

**Endpoint (CloudHub)**: `POST https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/soap/orderservice`
**Endpoint (Local)**: `POST http://localhost:8081/soap/orderservice`

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
Invoke-WebRequest -Uri "http://localhost:8082/soap/orderservice" -Method POST -Headers $headers -Body $body
```

---

### 2. GetOrder Operation

**Endpoint (CloudHub)**: `POST https://raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io/soap/orderservice`
**Endpoint (Local)**: `POST http://localhost:8081/soap/orderservice`

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
Invoke-WebRequest -Uri "http://localhost:8082/soap/orderservice" -Method POST -Headers $headers -Body $body
```

---

## Parameter Verification Testing

This endpoint is specifically designed to verify that `apiforge` correctly replaces URI and Query parameters.

- **Endpoint**: `http://localhost:8081/api/orders/{orderNumber}?source={source}&count={count}`

- **Authentication**: Basic (raks/admin)

- **Verification**: The response will echo the `orderNumber` from the path and the `source` from the query string. If `count` is provided (e.g., `count=10`), the API will simulate a large payload by returning 10 order objects in an array.

### PowerShell Test

```powershell
$auth = [Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes("raks:admin"))
# Test with both URI Param, Query Param, and Large Payload (count=5)
Invoke-RestMethod -Uri "http://localhost:8081/api/orders/ORD-TEST-999?source=PowerShell&count=5" -Headers @{Authorization=("Basic $auth")}
```

---

## mTLS Security Testing

The Mule project is configured with a Mutual TLS (mTLS) listener on port **8082**.

### 1. mTLS REST Wrapper

- **Local**: `https://localhost:8082/mtls/api/orders`
- **CloudHub**: `https://mule-worker-raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io:8082/mtls/api/orders`

### 2. mTLS SOAP Wrapper

- **Local**: `https://localhost:8082/mtls/soap/orderservice`
- **CloudHub**: `https://mule-worker-raksmule-ul5a1j.scqos5-2.usa-w1.cloudhub.io:8082/mtls/soap/orderservice`

> [!IMPORTANT]
> **CloudHub mTLS Setup**:
> 1. In **Runtime Manager** -> **Properties**, ensure **`mule.https.port`** is set to **`8082`**.
> 2. You **must** use the `mule-worker-` prefix and port `:8082` to reach the application directly. The standard CloudHub URL (port 443) does not support Mutual TLS.

### 3. Testing with API Forge

To test these endpoints in `apiforge`, you must provide the client certificate:

1. **Locate Certificates**:
   - The test certificates are in: `C:\raks\apiguard\reference\mule_projects\raksmule\src\main\resources\certs`
   - Use `client-keystore.p12` (Password: `password`).

2. **Configure Security in API Forge**:
   - Open the **Security & Certificates** accordion.
   - **Option A (Bundle)**: Select `client-keystore.p12`.
   - **Option B (Individual Files)**:
     - **Certificate**: Select `client.crt`.
     - **Private Key**: Select `client.key`.
   - **Passphrase**: Enter `password` (for Option A) or leave blank (for Option B if key is unencrypted).
   - **Trust CA**: Select `truststore.jks` or `server-keystore.jks` (which contains the root cert) if you get SSL validation errors.
   - Click **Validate Security Configuration**.

3. **Run Comparison**:
   - Use the `https://localhost:8082/...` URLs.
   - If configured correctly, Mule will accept the connection and the "mTLS REST Wrapper hit" log will appear in the Mule console.

---

## Summary Table

| Service | Operation | Method | Endpoint | Content-Type |
|---------|-----------|--------|----------|--------------|
| REST | Create Order | POST | `/api/orders` | application/json |
| REST | Get Orders | GET | `/api/orders` | - |
| REST | Get Order (Specific) | GET | `/api/orders/{orderNumber}` | - |
| REST | Combined Params | GET | `/api/orders/{orderNumber}?count=10&source=...` | application/json |
| REST | Large Simulation | GET | `/api/orders?count=100` | application/json |
| SOAP | CreateOrder | POST | `/soap/orderservice` | text/xml |
| SOAP | GetOrder | POST | `/soap/orderservice` | text/xml |

---

## Notes

- Standard HTTP runs on port **8081**
- Secure HTTPS (mTLS) runs on port **8082**
- **Basic Authentication required** for standard endpoints (user: `raks`, password: `admin`)
- Responses are dynamic (Large Payload) or static (Others)
- SOAP operations require proper XML envelope structure
- SOAP operations are distinguished by the request body content (CreateOrderRequest vs GetOrderRequest)
