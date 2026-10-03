# Надсилає справжні HTTP-запити до вже запущеного застосунку (run.ps1, java -jar або IntelliJ).
# Кожен запуск створює нове замовлення; -StepByStep додає паузи для пояснень.
param([string]$BaseUrl = 'http://localhost:8080', [switch]$StepByStep)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
function Next-Step { if ($StepByStep) { Read-Host 'Enter — наступний крок' | Out-Null } }
# Надсилає POST з JSON. Очікувані відмови (400) показує як текст, щоб продовжити демонстрацію.
function Send-Demo($Path, $Body) {
    try {
        Invoke-RestMethod -Uri "$BaseUrl$Path" -Method Post -ContentType 'application/json; charset=utf-8' `
            -Body ([System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json))) | ConvertTo-Json -Depth 5
    } catch {
        $message = $_.ErrorDetails.Message
        try { $message = ($message | ConvertFrom-Json).error } catch { }
        Write-Host "HTTP $([int]$_.Exception.Response.StatusCode): $message"
    }
}

Write-Host '1. Каталог (контекст Catalog)'
$products = Invoke-RestMethod "$BaseUrl/products"
$products | Format-Table name, price
$product = $products[0]
Next-Step
Write-Host '2. Створюємо замовлення-чернетку (контекст Ordering)'
$order = Invoke-RestMethod -Uri "$BaseUrl/orders" -Method Post -ContentType 'application/json' `
    -Body (@{ customerId = [guid]::NewGuid().ToString() } | ConvertTo-Json)
$order | ConvertTo-Json -Depth 5
Next-Step
Write-Host '3. Оформити порожнє замовлення: очікуємо HTTP 400'
Send-Demo "/orders/$($order.id)/place" @{}
Next-Step
Write-Host '4. Додаємо 2 шт. товару: Ordering бере назву й ціну з Catalog через контракт ProductCatalog'
Send-Demo "/orders/$($order.id)/items" @{ productId = $product.id; quantity = 2 }
Next-Step
Write-Host '5. Додаємо ще 98 шт. того самого товару: разом 100 > 99, очікуємо HTTP 400'
Send-Demo "/orders/$($order.id)/items" @{ productId = $product.id; quantity = 98 }
Next-Step
Write-Host '6. Неіснуючий товар: очікуємо HTTP 404'
Send-Demo "/orders/$($order.id)/items" @{ productId = [guid]::NewGuid().ToString(); quantity = 1 }
Next-Step
Write-Host '7. Оформлюємо замовлення'
Send-Demo "/orders/$($order.id)/place" @{}
Next-Step
Write-Host '8. Змінити оформлене замовлення: очікуємо HTTP 400'
Send-Demo "/orders/$($order.id)/items" @{ productId = $product.id; quantity = 1 }
Next-Step
Write-Host '9. Читаємо збережене замовлення'
Invoke-RestMethod "$BaseUrl/orders/$($order.id)" | ConvertTo-Json -Depth 5
Next-Step
Write-Host '10. Створюємо тимчасовий товар і видаляємо його з каталогу (HTTP 204)'
$temp = Invoke-RestMethod -Uri "$BaseUrl/products" -Method Post -ContentType 'application/json; charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes((@{ name = 'Тимчасовий товар'; description = ''; price = 1 } | ConvertTo-Json)))
Invoke-RestMethod -Uri "$BaseUrl/products/$($temp.id)" -Method Delete | Out-Null
Write-Host "Товар $($temp.id) видалено"
Next-Step
Write-Host '11. Читаємо видалений товар: очікуємо HTTP 404'
try { Invoke-RestMethod "$BaseUrl/products/$($temp.id)" | Out-Null }
catch {
    $message = $_.ErrorDetails.Message
    try { $message = ($message | ConvertFrom-Json).error } catch { }
    Write-Host "HTTP $([int]$_.Exception.Response.StatusCode): $message"
}
