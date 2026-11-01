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
Write-Host '6. Неіснуючий товар і кількість 0: AddOrderItemRequestValidator (ПР8) повертає обидві помилки, HTTP 400'
Send-Demo "/orders/$($order.id)/items" @{ productId = [guid]::NewGuid().ToString(); quantity = 0 }
Next-Step
Write-Host '7. Оформлюємо замовлення: OrderService публікує подію OrderPlaced'
Send-Demo "/orders/$($order.id)/place" @{}
Next-Step
Write-Host '7a. Реакції на подію (ПР2): сповіщення покупцю та статистика'
Invoke-RestMethod "$BaseUrl/notifications" | Select-Object -First 1 | ConvertTo-Json
Invoke-RestMethod "$BaseUrl/statistics" | ConvertTo-Json
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
Next-Step
Write-Host '12. ПР8: некоректний товар (порожня назва, ціна з трьома знаками після коми) — структурована відповідь 400'
try {
    Invoke-RestMethod -Uri "$BaseUrl/products" -Method Post -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes((@{ name = ' '; description = ''; price = 12.345 } | ConvertTo-Json))) | Out-Null
} catch {
    Write-Host "HTTP $([int]$_.Exception.Response.StatusCode):"
    $_.ErrorDetails.Message | ConvertFrom-Json | ConvertTo-Json -Depth 5
}
Next-Step
Write-Host '13. ПР8: товар з назвою, що вже є в каталозі (семантична перевірка з доступом до даних): HTTP 400'
Send-Demo '/products' @{ name = $product.name; description = ''; price = 1 }
Next-Step
# ПР9: прямий запит до Memcached (текстовий протокол) — лише щоб показати, що лежить у кеші.
function Invoke-Memcached([string]$Command, [string]$Server = '127.0.0.1', [int]$Port = 11211) {
    try {
        $client = [System.Net.Sockets.TcpClient]::new($Server, $Port)
        try {
            $stream = $client.GetStream()
            $bytes = [Text.Encoding]::ASCII.GetBytes("$Command`r`n")
            $stream.Write($bytes, 0, $bytes.Length)
            $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8)
            $lines = @()
            while ($true) {
                $line = $reader.ReadLine()
                $lines += $line
                if ($line -in 'END', 'DELETED', 'NOT_FOUND', 'ERROR' -or $null -eq $line) { break }
            }
            return $lines
        } finally { $client.Dispose() }
    } catch { return @('Memcached недоступний: застосунок працює без кешу (fallback до сховища)') }
}
function Show-CacheStats {
    $s = Invoke-Memcached 'stats' | Where-Object { $_ -match '^STAT (get_hits|get_misses|curr_items) ' }
    Write-Host ("   Memcached: " + (($s | ForEach-Object { $_ -replace '^STAT ', '' }) -join ', '))
}
Write-Host '14. ПР9: нове замовлення з товаром; перше читання — промах кешу (дані зі сховища), друге — влучання'
$cached = Invoke-RestMethod -Uri "$BaseUrl/orders" -Method Post -ContentType 'application/json' `
    -Body (@{ customerId = [guid]::NewGuid().ToString() } | ConvertTo-Json)
Send-Demo "/orders/$($cached.id)/items" @{ productId = $product.id; quantity = 1 } | Out-Null
Show-CacheStats
Invoke-RestMethod "$BaseUrl/orders/$($cached.id)" | Out-Null
Write-Host '   після 1-го GET:'; Show-CacheStats
Invoke-RestMethod "$BaseUrl/orders/$($cached.id)" | Out-Null
Write-Host '   після 2-го GET:'; Show-CacheStats
Write-Host "   Запис у кеші за ключем order:$($cached.id) (TTL 60 с, без customerId):"
Invoke-Memcached "get order:$($cached.id)" | ForEach-Object { "   $_" }
Next-Step
Write-Host '15. ПР9: додаємо ще товар — після збереження запис у кеші інвалідовано, наступний GET бачить нову суму'
Send-Demo "/orders/$($cached.id)/items" @{ productId = $product.id; quantity = 1 } | Out-Null
Write-Host "   get order:$($cached.id) одразу після зміни:"
Invoke-Memcached "get order:$($cached.id)" | ForEach-Object { "   $_" }
Invoke-RestMethod "$BaseUrl/orders/$($cached.id)" | Select-Object id, status, total | Format-List
