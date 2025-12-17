# Mule Secure Properties - Encryption Utility
# This script helps you encrypt passwords for use in secure.properties

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "   Mule Secure Properties - Encryption Tool" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host ""

# Get encryption key
Write-Host "Step 1: Enter your encryption key" -ForegroundColor Yellow
Write-Host "(This key will be used to encrypt/decrypt passwords)" -ForegroundColor Gray
Write-Host "(Store this key securely - you'll need it in mule-app.properties)" -ForegroundColor Gray
$encryptionKey = Read-Host "Encryption Key"

# Get password to encrypt
Write-Host ""
Write-Host "Step 2: Enter the password to encrypt" -ForegroundColor Yellow
$password = Read-Host "Password to encrypt" -AsSecureString
$passwordPlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($password))

# Simple AES encryption using .NET
Write-Host ""
Write-Host "Encrypting..." -ForegroundColor Green

try {
    $aes = [System.Security.Cryptography.Aes]::Create()
    $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
    $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
    
    # Generate key from passphrase
    $keyBytes = [System.Text.Encoding]::UTF8.GetBytes($encryptionKey.PadRight(32).Substring(0, 32))
    $aes.Key = $keyBytes
    $aes.GenerateIV()
    
    $encryptor = $aes.CreateEncryptor()
    $passwordBytes = [System.Text.Encoding]::UTF8.GetBytes($passwordPlain)
    
    # Encrypt
    $encrypted = $encryptor.TransformFinalBlock($passwordBytes, 0, $passwordBytes.Length)
    
    # Combine IV and encrypted data
    $combined = $aes.IV + $encrypted
    $encryptedBase64 = [Convert]::ToBase64String($combined)
    
    Write-Host ""
    Write-Host "==================================================" -ForegroundColor Green
    Write-Host "Encryption Successful!" -ForegroundColor Green
    Write-Host "==================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Encrypted Value (copy this):" -ForegroundColor Yellow
    Write-Host "![${encryptedBase64}]" -ForegroundColor White
    Write-Host ""
    Write-Host "Add this to secure.properties:" -ForegroundColor Yellow
    Write-Host "smtp.password.encrypted=![${encryptedBase64}]" -ForegroundColor White
    Write-Host ""
    Write-Host "Add this to mule-app.properties:" -ForegroundColor Yellow
    Write-Host "encryption.key=${encryptionKey}" -ForegroundColor White
    Write-Host ""
    Write-Host "IMPORTANT: Store the encryption key securely!" -ForegroundColor Red
    Write-Host ""
    
    $aes.Dispose()
}
catch {
    Write-Host "Error during encryption: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
