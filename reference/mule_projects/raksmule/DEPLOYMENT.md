# RaksMule - Deployment Guide

## ✅ Build Status

**Successfully Built**: `raksmule-1.0.0-mule-application.jar`

## 📦 Single JAR Deployment

**YES** - Both SOAP and REST services are in a **single deployable JAR**. Mule 4 fully supports multiple API types in one application.

## 🚀 Deployed Endpoints

### SOAP Service

- **Endpoint**: `http://localhost:8081/soap/orderservice`
- **WSDL**: `http://localhost:8081/soap/orderservice?wsdl` (GET request)
- **Operations**:
  - `CreateOrder`
  - `GetOrder`

### REST API

- **Base URL**: `http://localhost:8081/api`
- **Endpoints**:
  - `POST /api/orders` - Create order
  - `GET /api/orders/{orderNumber}` - Get order details

## 🧪 Testing Commands

### SOAP CreateOrder

```powershell
$headers = @{
    "Content-Type" = "text/xml"
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
Invoke-WebRequest -Uri "http://localhost:8081/soap/orderservice" -Method POST -Headers $headers -Body $body
```

### SOAP GetOrder

```powershell
$headers = @{
    "Content-Type" = "text/xml"
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
Invoke-WebRequest -Uri "http://localhost:8081/soap/orderservice" -Method POST -Headers $headers -Body $body
```

### REST Create Order

```powershell
$headers = @{
    "Content-Type" = "application/json"
}
$body = @"
{
  "customerName": "John Doe",
  "customerEmail": "john.doe@example.com",
  "orderDate": "2026-01-18",
  "productName": "Widget Pro",
  "quantity": 5,
  "totalAmount": 249.99
}
"@
Invoke-WebRequest -Uri "http://localhost:8081/api/orders" -Method POST -Headers $headers -Body $body
```

### REST Get Order

```powershell
Invoke-WebRequest -Uri "http://localhost:8081/api/orders/ORD-20260118-001" -Method GET
```

## 📋 For API URL Comparison Testing

Use these endpoints in `C:\raks\apiguard\apiurlcomparison`:

1. **SOAP Endpoint**: `http://localhost:8081/soap/orderservice`
2. **REST Endpoint**: `http://localhost:8081/api/orders`

Both endpoints are now live and ready for testing!

## 🔄 Redeploy

To redeploy after changes:

```powershell
cd C:\raks\apiguard\reference\mule_projects\raksmule
mvn clean package -DskipTests
Copy-Item "target\raksmule-1.0.0-mule-application.jar" -Destination "C:\raks\mule-enterprise-standalone-4.10.2\apps\" -Force
```

## ⚠️ Note

- **Port 8081** for HTTP and **8082** for mTLS
- Authentication is **not implemented** in this simplified version
- Responses are **static** for simplicity and reliability
- Both SOAP and REST work independently in the same JAR

## ✅ Deployment Status

**Successfully Deployed!** 🎉

- Application: `raksmule-1.0.0-mule-application`
- Status: **DEPLOYED**
- Plugins: HTTP 1.9.3, Sockets 1.2.4

### Verified Endpoints

- ✅ REST GET: `http://localhost:8081/api/orders/{orderNumber}` - Working
- ✅ REST POST: `http://localhost:8081/api/orders` - Working
- ✅ SOAP WSDL: `http://localhost:8081/soap/orderservice?wsdl` - Working
- ✅ SOAP Service: `http://localhost:8081/soap/orderservice` - Ready

**Ready for API URL Comparison testing!**
