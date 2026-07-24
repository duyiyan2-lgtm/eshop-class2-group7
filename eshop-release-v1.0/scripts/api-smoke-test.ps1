param(
    [string]$BaseUrl = "http://localhost:8088/api",
    [switch]$KeepData
)

$ErrorActionPreference = "Stop"

function Read-DotEnv {
    param([string]$Path)
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^\s*([^#][^=]*)=(.*)$') {
            $values[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    return $values
}

function Assert-Equal {
    param(
        [object]$Actual,
        [object]$Expected,
        [string]$Label
    )
    if ("$Actual" -ne "$Expected") {
        throw "$Label failed: expected '$Expected', actual '$Actual'"
    }
    Write-Output "PASS  $Label"
}

function Remove-SmokeFixtures {
    param(
        [string]$ProjectRoot,
        [hashtable]$Settings,
        [string]$UserA,
        [string]$UserB,
        [string]$CategoryName,
        [string]$ProductName,
        [string]$SkuCode,
        [string]$DisabledParentName
    )
    try {
        $composeFile = Join-Path $ProjectRoot "docker-compose.yml"
        $containerId = (& docker compose --project-directory $ProjectRoot -f $composeFile ps -q mysql).Trim()
        if (-not $containerId) {
            Write-Warning "MySQL container was not found; smoke fixtures were kept."
            return
        }
        $sql = @"
START TRANSACTION;
DELETE FROM payment_record WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB'));
DELETE FROM order_status_log WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB')));
DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB')));
DELETE FROM orders WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB'));
DELETE FROM cart_item WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB'));
DELETE FROM user_address WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB'));
DELETE FROM sys_user WHERE username IN ('$UserA', '$UserB');
DELETE FROM cart_item WHERE sku_id IN (SELECT id FROM product_sku WHERE sku_code = '$SkuCode');
DELETE FROM product_sku WHERE sku_code = '$SkuCode';
DELETE FROM product WHERE name = '$ProductName';
DELETE FROM category WHERE name IN ('$CategoryName', '$DisabledParentName');
COMMIT;
"@
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & docker exec $containerId mysql -N -uroot "-p$($Settings['MYSQL_ROOT_PASSWORD'])" `
            $Settings["MYSQL_DATABASE"] -e $sql 2>&1 | Out-Null
        $cleanupExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorActionPreference
        if ($cleanupExitCode -ne 0) {
            Write-Warning "MySQL smoke fixture cleanup failed."
            return
        }
        Write-Output "PASS  smoke fixture cleanup"
    } catch {
        Write-Warning "Smoke fixture cleanup failed: $($_.Exception.Message)"
    }
}

function Set-SmokeOrderExpired {
    param(
        [string]$ProjectRoot,
        [hashtable]$Settings,
        [long]$OrderId
    )
    $composeFile = Join-Path $ProjectRoot "docker-compose.yml"
    $containerId = (& docker compose --project-directory $ProjectRoot -f $composeFile ps -q mysql).Trim()
    if (-not $containerId) {
        throw "MySQL container was not found."
    }
    $sql = "UPDATE orders SET created_at = DATE_SUB(NOW(), INTERVAL 31 MINUTE) WHERE id = $OrderId;"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & docker exec $containerId mysql -N -uroot "-p$($Settings['MYSQL_ROOT_PASSWORD'])" `
        $Settings["MYSQL_DATABASE"] -e $sql 2>&1 | Out-Null
    $updateExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($updateExitCode -ne 0) {
        throw "Could not backdate smoke order $OrderId."
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$Token,
        [int[]]$ExpectedStatus = @(200)
    )

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method),
        $Path.TrimStart('/'))
    try {
        if ($Token) {
            $request.Headers.Authorization =
                [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
        }
        if ($null -ne $Body) {
            $json = ConvertTo-Json $Body -Depth 10 -Compress
            $request.Content = [System.Net.Http.StringContent]::new(
                $json,
                [System.Text.Encoding]::UTF8,
                "application/json")
        }
        $response = $script:Client.SendAsync($request).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $payload = $null
        if ($text) {
            try {
                $payload = $text | ConvertFrom-Json
            } catch {
                throw "Response is not valid JSON: $text"
            }
        }
        $status = [int]$response.StatusCode
        if ($status -notin $ExpectedStatus) {
            throw "$Method $Path returned HTTP $status, body: $text"
        }
        $hasBusinessCode = $null -ne $payload -and
            $payload.PSObject.Properties.Name -contains "code"
        if ($status -ge 200 -and $status -lt 300 -and $hasBusinessCode -and $payload.code -ne 0) {
            throw "$Method $Path returned business code $($payload.code): $($payload.message)"
        }
        return [pscustomobject]@{
            Status = $status
            Body = $payload
        }
    } finally {
        $request.Dispose()
    }
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot ".env"
if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing $envFile. Copy .env.example to .env first."
}

$settings = Read-DotEnv $envFile
$adminUsername = $settings["BOOTSTRAP_ADMIN_USERNAME"]
$adminPassword = $settings["BOOTSTRAP_ADMIN_PASSWORD"]
if (-not $adminUsername -or -not $adminPassword) {
    throw "BOOTSTRAP_ADMIN_USERNAME or BOOTSTRAP_ADMIN_PASSWORD is missing in .env"
}

Add-Type -AssemblyName System.Net.Http
$script:Client = [System.Net.Http.HttpClient]::new()
$script:Client.BaseAddress = [Uri]::new($BaseUrl.TrimEnd('/') + "/")
$script:Client.Timeout = [TimeSpan]::FromSeconds(15)

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$userA = "smoke_a_$suffix"
$userB = "smoke_b_$suffix"
$password = "Smoke123456"
$categoryName = "Smoke Category $suffix"
$productName = "Smoke Product $suffix"
$skuCode = "SMOKE-$suffix"
$disabledParentName = "Disabled Parent $suffix"

try {
    $health = Invoke-Api GET "actuator/health" $null $null
    Assert-Equal $health.Body.status "UP" "health endpoint"

    $adminLogin = Invoke-Api POST "auth/login" @{
        username = $adminUsername
        password = $adminPassword
    } $null
    $adminToken = $adminLogin.Body.data.token
    $adminId = $adminLogin.Body.data.userId
    Assert-Equal $adminLogin.Body.data.role "ADMIN" "administrator login"

    foreach ($username in @($userA, $userB)) {
        $registered = Invoke-Api POST "auth/register" @{
            username = $username
            password = $password
            nickname = "Smoke User"
        } $null
        Assert-Equal $registered.Status 200 "register $username"
    }

    $duplicate = Invoke-Api POST "auth/register" @{
        username = $userA
        password = $password
        nickname = "Duplicate User"
    } $null @(409)
    Assert-Equal $duplicate.Body.code 40901 "duplicate registration"

    $wrongPassword = Invoke-Api POST "auth/login" @{
        username = $userA
        password = "wrong-password"
    } $null @(401)
    Assert-Equal $wrongPassword.Body.code 40102 "wrong password"

    $oversizedUtf8Password = -join (1..30 | ForEach-Object { [char]0x5BC6 })
    $utf8Password = Invoke-Api POST "auth/register" @{
        username = "utf8_$suffix"
        password = $oversizedUtf8Password
        nickname = "UTF8 Password Test"
    } $null @(400)
    Assert-Equal $utf8Password.Body.code 40002 "BCrypt UTF-8 byte limit"

    $loginA = Invoke-Api POST "auth/login" @{
        username = $userA
        password = $password
    } $null
    $tokenA = $loginA.Body.data.token
    $userAId = $loginA.Body.data.userId
    $loginB = Invoke-Api POST "auth/login" @{
        username = $userB
        password = $password
    } $null
    $tokenB = $loginB.Body.data.token

    $forbidden = Invoke-Api GET "admin/users" $null $tokenA @(403)
    Assert-Equal $forbidden.Body.code 40301 "ordinary user blocked from admin API"

    $invalidOnSale = Invoke-Api POST "admin/products" @{
        categoryId = 999999999
        name = "Missing Category Product"
        status = "ON_SALE"
    } $adminToken @(404)
    Assert-Equal $invalidOnSale.Body.code 40410 "nonexistent category rejected"

    $category = Invoke-Api POST "admin/categories" @{
        name = $categoryName
        sortOrder = 99
        status = "ENABLED"
    } $adminToken
    $categoryId = $category.Body.data.id

    $disabledParent = Invoke-Api POST "admin/categories" @{
        name = $disabledParentName
        sortOrder = 100
        status = "DISABLED"
    } $adminToken
    $disabledParentId = $disabledParent.Body.data.id
    $orphanChild = Invoke-Api POST "admin/categories" @{
        parentId = $disabledParentId
        name = "Invalid Enabled Child $suffix"
        sortOrder = 1
        status = "ENABLED"
    } $adminToken @(409)
    Assert-Equal $orphanChild.Body.code 40924 "enabled child below disabled parent rejected"
    $null = Invoke-Api DELETE "admin/categories/$disabledParentId" $null $adminToken

    $product = Invoke-Api POST "admin/products" @{
        categoryId = $categoryId
        name = $productName
        subtitle = "Real MySQL smoke test"
        mainImage = "/api/demo/phone.svg"
        detail = "Only used by the automated smoke test"
        status = "DRAFT"
    } $adminToken
    $productId = $product.Body.data.id

    $invalidSku = Invoke-Api POST "admin/products/$productId/skus" @{
        skuCode = "$skuCode-BAD"
        specsJson = "not-json"
        price = 12.34
        stock = 10
        status = "ENABLED"
    } $adminToken @(400)
    Assert-Equal $invalidSku.Body.code 40022 "invalid SKU JSON rejected"

    $sku = Invoke-Api POST "admin/products/$productId/skus" @{
        skuCode = $skuCode
        specsJson = '{"color":"test-blue"}'
        price = 12.34
        stock = 10
        status = "ENABLED"
    } $adminToken
    $skuId = $sku.Body.data.id

    $onSale = Invoke-Api PATCH "admin/products/$productId/status" @{
        status = "ON_SALE"
    } $adminToken
    Assert-Equal $onSale.Body.data.status "ON_SALE" "put product on sale"

    $publicProduct = Invoke-Api GET "products/$productId" $null $null
    Assert-Equal $publicProduct.Body.data.skus[0].id $skuId "public product detail"

    $search = Invoke-Api GET ("products?keyword=" + [Uri]::EscapeDataString($productName)) $null $null
    Assert-Equal $search.Body.data.total 1 "product keyword search"

    $address = Invoke-Api POST "addresses" @{
        receiverName = "Smoke Receiver"
        phone = "13800138000"
        province = "Guangdong"
        city = "Guangzhou"
        district = "Tianhe"
        detail = "Test Road 1"
        isDefault = $true
    } $tokenA
    $addressId = $address.Body.data.id
    Assert-Equal $address.Body.data.isDefault $true "first address is default"

    $cart = Invoke-Api POST "cart" @{
        skuId = $skuId
        quantity = 2
    } $tokenA
    $cartItemId = $cart.Body.data.id
    Assert-Equal $cart.Body.data.subtotal 24.68 "cart subtotal"

    $foreignCart = Invoke-Api PATCH "cart/$cartItemId" @{
        quantity = 1
    } $tokenB @(404)
    Assert-Equal $foreignCart.Body.code 40413 "cart ownership isolation"

    $null = Invoke-Api PATCH "cart/$cartItemId" @{
        selected = $false
    } $tokenA
    $emptyCartOrder = Invoke-Api POST "orders" @{
        addressId = $addressId
    } $tokenA @(400)
    Assert-Equal $emptyCartOrder.Body.code 40030 "unselected cart cannot be settled"
    $null = Invoke-Api PATCH "cart/$cartItemId" @{
        selected = $true
    } $tokenA

    $order = Invoke-Api POST "orders" @{
        addressId = $addressId
        remark = "Smoke test order"
    } $tokenA
    $orderId = $order.Body.data.id
    Assert-Equal $order.Body.data.status "PENDING_PAYMENT" "create order"
    Assert-Equal $order.Body.data.totalAmount 24.68 "order total"

    $stockAfterOrder = Invoke-Api GET "admin/products/$productId" $null $adminToken
    Assert-Equal $stockAfterOrder.Body.data.skus[0].stock 8 "stock deduction"

    $null = Invoke-Api PATCH "admin/products/$productId/status" @{
        status = "OFF_SALE"
    } $adminToken
    $activeSkuDelete = Invoke-Api DELETE "admin/skus/$skuId" $null $adminToken @(409)
    Assert-Equal $activeSkuDelete.Body.code 40927 "active order blocks SKU deletion"
    $null = Invoke-Api PATCH "admin/products/$productId/status" @{
        status = "ON_SALE"
    } $adminToken

    $foreignOrder = Invoke-Api GET "orders/$orderId" $null $tokenB @(404)
    Assert-Equal $foreignOrder.Body.code 40415 "order ownership isolation"

    $payment = Invoke-Api POST "orders/$orderId/pay" $null $tokenA
    Assert-Equal $payment.Body.data.status "SUCCESS" "simulated payment"
    $duplicatePay = Invoke-Api POST "orders/$orderId/pay" $null $tokenA @(409)
    Assert-Equal $duplicatePay.Body.code 40912 "duplicate payment rejected"

    $shipped = Invoke-Api POST "admin/orders/$orderId/ship" $null $adminToken
    Assert-Equal $shipped.Body.data.status "SHIPPED" "administrator shipment"
    $completed = Invoke-Api POST "orders/$orderId/confirm" $null $tokenA
    Assert-Equal $completed.Body.data.status "COMPLETED" "confirm receipt"
    $logs = Invoke-Api GET "orders/$orderId/logs" $null $tokenA
    Assert-Equal @($logs.Body.data).Count 4 "order status logs"

    $cartForCancel = Invoke-Api POST "cart" @{
        skuId = $skuId
        quantity = 3
    } $tokenA
    $cancelOrder = Invoke-Api POST "orders" @{
        addressId = $addressId
    } $tokenA
    $cancelOrderId = $cancelOrder.Body.data.id
    $stockBeforeCancel = Invoke-Api GET "admin/products/$productId" $null $adminToken
    Assert-Equal $stockBeforeCancel.Body.data.skus[0].stock 5 "second stock deduction"
    Set-SmokeOrderExpired `
        -ProjectRoot $projectRoot `
        -Settings $settings `
        -OrderId $cancelOrderId
    $latePayment = Invoke-Api POST "orders/$cancelOrderId/pay" $null $tokenA @(409)
    Assert-Equal $latePayment.Body.code 40926 "expired order payment rejected before scheduler scan"
    $canceled = Invoke-Api POST "orders/$cancelOrderId/cancel" $null $tokenA
    Assert-Equal $canceled.Body.data.status "CANCELED" "cancel pending order"
    $stockAfterCancel = Invoke-Api GET "admin/products/$productId" $null $adminToken
    Assert-Equal $stockAfterCancel.Body.data.skus[0].stock 8 "stock restored once"
    $duplicateCancel = Invoke-Api POST "orders/$cancelOrderId/cancel" $null $tokenA @(409)
    Assert-Equal $duplicateCancel.Body.code 40912 "duplicate cancellation rejected"

    $invalidFilter = Invoke-Api GET "orders?status=UNKNOWN" $null $tokenA @(400)
    Assert-Equal $invalidFilter.Body.code 40001 "invalid order status filter"
    $selfDisable = Invoke-Api PATCH "admin/users/$adminId/status" @{
        status = "DISABLED"
    } $adminToken @(409)
    Assert-Equal $selfDisable.Body.code 40918 "administrator cannot disable self"

    $disabled = Invoke-Api PATCH "admin/users/$userAId/status" @{
        status = "DISABLED"
    } $adminToken
    Assert-Equal $disabled.Body.data.status "DISABLED" "disable user"
    $disabledToken = Invoke-Api GET "auth/me" $null $tokenA @(401)
    Assert-Equal $disabledToken.Body.code 40101 "disabled user's old JWT rejected"
    $enabled = Invoke-Api PATCH "admin/users/$userAId/status" @{
        status = "ENABLED"
    } $adminToken
    Assert-Equal $enabled.Body.data.status "ENABLED" "re-enable user"

    $null = Invoke-Api PATCH "admin/products/$productId/status" @{
        status = "OFF_SALE"
    } $adminToken
    $null = Invoke-Api DELETE "admin/skus/$skuId" $null $adminToken
    Write-Output "PASS  delete SKU after active orders finish"
    $null = Invoke-Api DELETE "admin/products/$productId" $null $adminToken
    $null = Invoke-Api DELETE "admin/categories/$categoryId" $null $adminToken
    Write-Output "PASS  test catalog cleanup"

    Write-Output ""
    Write-Output "API smoke test completed successfully."
} finally {
    $script:Client.Dispose()
    if (-not $KeepData) {
        Remove-SmokeFixtures `
            -ProjectRoot $projectRoot `
            -Settings $settings `
            -UserA $userA `
            -UserB $userB `
            -CategoryName $categoryName `
            -ProductName $productName `
            -SkuCode $skuCode `
            -DisabledParentName $disabledParentName
    }
}
