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

function Assert-True {
    param(
        [bool]$Condition,
        [string]$Label
    )
    if (-not $Condition) {
        throw "$Label failed"
    }
    Write-Output "PASS  $Label"
}

function Remove-SmokeFixtures {
    param(
        [string]$ProjectRoot,
        [hashtable]$Settings,
        [string]$UserA,
        [string]$UserB,
        [string]$SellerUser,
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
DELETE FROM product_review WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB'));
DELETE FROM product_favorite WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB'));
DELETE FROM product_browse_history WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB'));
DELETE FROM payment_record WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB'));
DELETE FROM order_status_log WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB')));
DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB')));
DELETE FROM orders WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB'));
DELETE FROM cart_item WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB'));
DELETE FROM user_address WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('$UserA', '$UserB'));
DELETE FROM sys_user WHERE username IN ('$UserA', '$UserB', '$SellerUser');
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
$sellerUser = "smoke_seller_$suffix"
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

    $dashboard = Invoke-Api GET "admin/dashboard/summary" $null $adminToken
    Assert-True ($dashboard.Body.data.userCount -ge 0) "dashboard buyer count"
    Assert-True ($dashboard.Body.data.productCount -ge $dashboard.Body.data.onSaleProductCount) `
        "dashboard product counts"
    $dashboardOrderTotal = [long]$dashboard.Body.data.pendingPaymentOrderCount +
        [long]$dashboard.Body.data.paidOrderCount +
        [long]$dashboard.Body.data.shippedOrderCount +
        [long]$dashboard.Body.data.completedOrderCount +
        [long]$dashboard.Body.data.canceledOrderCount
    Assert-Equal $dashboardOrderTotal $dashboard.Body.data.orderCount "dashboard order counts"
    Assert-True ([decimal]$dashboard.Body.data.paidSalesAmount -ge 0) "dashboard sales amount"
    Assert-True ([long]$dashboard.Body.data.lowStockSkuCount -ge 0) "dashboard low stock count"

    foreach ($username in @($userA, $userB)) {
        $registered = Invoke-Api POST "auth/register" @{
            username = $username
            password = $password
            nickname = "Smoke User"
        } $null
        Assert-Equal $registered.Status 200 "register $username"
    }

    $sellerCreated = Invoke-Api POST "admin/users" @{
        username = $sellerUser
        password = $password
        nickname = "Smoke Seller"
        phone = "13800138001"
        role = "SELLER"
        status = "ENABLED"
    } $adminToken
    $sellerId = $sellerCreated.Body.data.id
    Assert-Equal $sellerCreated.Body.data.role "SELLER" "administrator creates seller"
    $duplicateSeller = Invoke-Api POST "admin/users" @{
        username = $sellerUser
        password = $password
        nickname = "Duplicate Seller"
        role = "SELLER"
        status = "ENABLED"
    } $adminToken @(409)
    Assert-Equal $duplicateSeller.Body.code 40901 "duplicate managed account rejected"
    $invalidManagedRole = Invoke-Api POST "admin/users" @{
        username = "invalid_admin_$suffix"
        password = $password
        nickname = "Invalid Admin"
        role = "ADMIN"
        status = "ENABLED"
    } $adminToken @(400)
    Assert-Equal $invalidManagedRole.Body.code 40001 "platform admin role cannot be delegated"
    $managedSummary = Invoke-Api GET "admin/users/summary" $null $adminToken
    Assert-True ([long]$managedSummary.Body.data.buyerCount -ge 2) "managed buyer account summary"
    Assert-Equal $managedSummary.Body.data.sellerCount 1 "managed seller account summary"
    $sellerList = Invoke-Api GET "admin/users?role=SELLER" $null $adminToken
    Assert-Equal $sellerList.Body.data.total 1 "seller account filter"
    Assert-Equal $sellerList.Body.data.records[0].username $sellerUser "seller account detail"
    $sellerLogin = Invoke-Api POST "auth/login" @{
        username = $sellerUser
        password = $password
    } $null
    $sellerToken = $sellerLogin.Body.data.token
    Assert-Equal $sellerLogin.Body.data.role "SELLER" "seller login"
    $sellerDashboard = Invoke-Api GET "admin/dashboard/summary" $null $sellerToken
    Assert-True ($null -ne $sellerDashboard.Body.data.productCount) "seller accesses merchant dashboard"
    $sellerProducts = Invoke-Api GET "admin/products?current=1&size=1" $null $sellerToken
    Assert-True ($null -ne $sellerProducts.Body.data.total) "seller accesses product management"
    $sellerUsersForbidden = Invoke-Api GET "admin/users" $null $sellerToken @(403)
    Assert-Equal $sellerUsersForbidden.Body.code 40301 "seller blocked from platform account management"
    $sellerToBuyer = Invoke-Api PATCH "admin/users/$sellerId/role" @{
        role = "USER"
    } $adminToken
    Assert-Equal $sellerToBuyer.Body.data.role "USER" "administrator changes seller to buyer"
    $oldSellerTokenForbidden = Invoke-Api GET "admin/dashboard/summary" $null $sellerToken @(403)
    Assert-Equal $oldSellerTokenForbidden.Body.code 40301 "role change affects old seller token"
    $sellerRestored = Invoke-Api PATCH "admin/users/$sellerId/role" @{
        role = "SELLER"
    } $adminToken
    Assert-Equal $sellerRestored.Body.data.role "SELLER" "administrator restores seller role"

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

    $updatedProfile = Invoke-Api PUT "auth/me" @{
        nickname = "Smoke Updated"
        phone = "138 0013-8000"
    } $tokenA
    Assert-Equal $updatedProfile.Body.data.nickname "Smoke Updated" "update current user nickname"
    $currentProfile = Invoke-Api GET "auth/me" $null $tokenA
    Assert-Equal $currentProfile.Body.data.phone "138 0013-8000" "current user profile persisted"

    $forbidden = Invoke-Api GET "admin/users" $null $tokenA @(403)
    Assert-Equal $forbidden.Body.code 40301 "ordinary user blocked from admin API"
    $dashboardForbidden = Invoke-Api GET "admin/dashboard/summary" $null $tokenA @(403)
    Assert-Equal $dashboardForbidden.Body.code 40301 "ordinary user blocked from dashboard"
    $salesTrendForbidden = Invoke-Api GET "admin/dashboard/sales-trend" $null $tokenA @(403)
    Assert-Equal $salesTrendForbidden.Body.code 40301 "ordinary user blocked from sales trend"
    $topProductsForbidden = Invoke-Api GET "admin/dashboard/top-products" $null $tokenA @(403)
    Assert-Equal $topProductsForbidden.Body.code 40301 "ordinary user blocked from top products"
    $inventoryForbidden = Invoke-Api GET "admin/inventory/alerts" $null $tokenA @(403)
    Assert-Equal $inventoryForbidden.Body.code 40301 "ordinary user blocked from inventory alerts"
    $reviewsForbidden = Invoke-Api GET "admin/reviews" $null $tokenA @(403)
    Assert-Equal $reviewsForbidden.Body.code 40301 "ordinary user blocked from review management"

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

    $draftFavorite = Invoke-Api POST "favorites/$productId" $null $tokenA @(409)
    Assert-Equal $draftFavorite.Body.code 40913 "draft product cannot be favorited"

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

    $inventoryAlerts = Invoke-Api GET (
        "admin/inventory/alerts?threshold=10&keyword=" + [Uri]::EscapeDataString($productName)
    ) $null $adminToken
    Assert-Equal $inventoryAlerts.Body.data.total 1 "inventory alert keyword and threshold"
    Assert-Equal $inventoryAlerts.Body.data.records[0].skuId $skuId "inventory alert SKU detail"
    Assert-Equal $inventoryAlerts.Body.data.records[0].stock 10 "inventory alert stock"
    $dashboardWithLowStock = Invoke-Api GET "admin/dashboard/summary" $null $adminToken
    Assert-True ([long]$dashboardWithLowStock.Body.data.lowStockSkuCount -ge 1) `
        "dashboard reflects low stock SKU"

    $publicProduct = Invoke-Api GET "products/$productId" $null $null
    Assert-Equal $publicProduct.Body.data.skus[0].id $skuId "public product detail"

    $search = Invoke-Api GET ("products?keyword=" + [Uri]::EscapeDataString($productName)) $null $null
    Assert-Equal $search.Body.data.total 1 "product keyword search"

    $anonymousHistory = Invoke-Api GET "browse-history" $null $null @(401)
    Assert-Equal $anonymousHistory.Body.code 40101 "browse history requires login"
    $null = Invoke-Api POST "browse-history/$productId" $null $tokenA
    $null = Invoke-Api POST "browse-history/$productId" $null $tokenA
    $historyPage = Invoke-Api GET "browse-history?current=1&size=10" $null $tokenA
    Assert-Equal $historyPage.Body.data.total 1 "duplicate browse history is idempotent"
    Assert-Equal $historyPage.Body.data.records[0].productId $productId "browse history product detail"
    Assert-Equal $historyPage.Body.data.records[0].totalStock 10 "browse history product stock"
    $foreignHistory = Invoke-Api GET "browse-history?current=1&size=10" $null $tokenB
    Assert-Equal $foreignHistory.Body.data.total 0 "browse history ownership isolation"
    $null = Invoke-Api DELETE "browse-history/$productId" $null $tokenB
    $historyAfterForeignRemoval = Invoke-Api GET "browse-history" $null $tokenA
    Assert-Equal $historyAfterForeignRemoval.Body.data.total 1 "foreign history removal does not affect owner"
    $null = Invoke-Api DELETE "browse-history/$productId" $null $tokenA
    $null = Invoke-Api DELETE "browse-history/$productId" $null $tokenA
    $null = Invoke-Api POST "browse-history/$productId" $null $tokenA
    $null = Invoke-Api DELETE "browse-history" $null $tokenA
    $null = Invoke-Api DELETE "browse-history" $null $tokenA
    $emptyHistory = Invoke-Api GET "browse-history" $null $tokenA
    Assert-Equal $emptyHistory.Body.data.total 0 "browse history clear is idempotent"

    $anonymousFavorites = Invoke-Api GET "favorites" $null $null @(401)
    Assert-Equal $anonymousFavorites.Body.code 40101 "favorite list requires login"
    $null = Invoke-Api POST "favorites/$productId" $null $tokenA
    Write-Output "PASS  add product favorite"
    $null = Invoke-Api POST "favorites/$productId" $null $tokenA
    Write-Output "PASS  duplicate favorite is idempotent"
    $favoriteStatus = Invoke-Api GET "favorites/$productId/status" $null $tokenA
    Assert-Equal $favoriteStatus.Body.data.favorited $true "favorite status"
    $foreignFavoriteStatus = Invoke-Api GET "favorites/$productId/status" $null $tokenB
    Assert-Equal $foreignFavoriteStatus.Body.data.favorited $false "favorite ownership isolation"
    $favoritePage = Invoke-Api GET "favorites?current=1&size=10" $null $tokenA
    Assert-Equal $favoritePage.Body.data.total 1 "favorite list total"
    Assert-Equal $favoritePage.Body.data.records[0].id $productId "favorite product detail"
    Assert-Equal $favoritePage.Body.data.records[0].totalStock 10 "favorite product stock"
    $null = Invoke-Api DELETE "favorites/$productId" $null $tokenB
    $favoriteStillExists = Invoke-Api GET "favorites/$productId/status" $null $tokenA
    Assert-Equal $favoriteStillExists.Body.data.favorited $true "foreign removal does not affect favorite"
    $null = Invoke-Api DELETE "favorites/$productId" $null $tokenA
    $null = Invoke-Api DELETE "favorites/$productId" $null $tokenA
    $removedFavoriteStatus = Invoke-Api GET "favorites/$productId/status" $null $tokenA
    Assert-Equal $removedFavoriteStatus.Body.data.favorited $false "favorite removal is idempotent"

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
    $orderItemId = $order.Body.data.items[0].id
    Assert-Equal $order.Body.data.status "PENDING_PAYMENT" "create order"
    Assert-Equal $order.Body.data.totalAmount 24.68 "order total"
    $earlyReview = Invoke-Api POST "reviews" @{
        orderItemId = $orderItemId
        rating = 5
        content = "Order is not completed yet"
    } $tokenA @(409)
    Assert-Equal $earlyReview.Body.code 40929 "unfinished order cannot be reviewed"

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

    $foreignReview = Invoke-Api POST "reviews" @{
        orderItemId = $orderItemId
        rating = 5
        content = "Foreign order review"
    } $tokenB @(404)
    Assert-Equal $foreignReview.Body.code 40417 "review ownership isolation"
    $tooManyImages = Invoke-Api POST "reviews" @{
        orderItemId = $orderItemId
        rating = 5
        content = "Too many review images"
        imageUrls = @(
            "/api/uploads/one.png",
            "/api/uploads/two.png",
            "/api/uploads/three.png",
            "/api/uploads/four.png"
        )
    } $tokenA @(400)
    Assert-Equal $tooManyImages.Body.code 40033 "review image count limit"
    $invalidImageUrl = Invoke-Api POST "reviews" @{
        orderItemId = $orderItemId
        rating = 5
        content = "Invalid review image"
        imageUrls = @("https://example.com/external.png")
    } $tokenA @(400)
    Assert-Equal $invalidImageUrl.Body.code 40034 "review image URL validation"
    $createdReview = Invoke-Api POST "reviews" @{
        orderItemId = $orderItemId
        rating = 5
        content = "  Smoke review works  "
        imageUrls = @(
            "/api/uploads/smoke-review-one.png",
            "/api/uploads/smoke-review-two.jpg"
        )
    } $tokenA
    Assert-Equal $createdReview.Body.data.content "Smoke review works" "create trimmed product review"
    Assert-Equal @($createdReview.Body.data.imageUrls).Count 2 "create review with images"
    Assert-Equal $createdReview.Body.data.imageUrls[0] "/api/uploads/smoke-review-one.png" `
        "created review image detail"
    $duplicateReview = Invoke-Api POST "reviews" @{
        orderItemId = $orderItemId
        rating = 4
        content = "Duplicate review"
    } $tokenA @(409)
    Assert-Equal $duplicateReview.Body.code 40930 "duplicate review rejected"
    $publicReviews = Invoke-Api GET "products/$productId/reviews?current=1&size=10" $null $null
    Assert-Equal $publicReviews.Body.data.total 1 "public product review list"
    Assert-Equal $publicReviews.Body.data.records[0].rating 5 "public product review rating"
    Assert-Equal @($publicReviews.Body.data.records[0].imageUrls).Count 2 `
        "public product review images"
    Assert-True (-not ($publicReviews.Body.data.records[0].PSObject.Properties.Name -contains "orderId")) `
        "public review hides order identifiers"
    $reviewSummary = Invoke-Api GET "products/$productId/reviews/summary" $null $null
    Assert-Equal $reviewSummary.Body.data.total 1 "product review summary total"
    Assert-True ([decimal]$reviewSummary.Body.data.averageRating -eq 5.0) `
        "product review average rating"
    Assert-Equal $reviewSummary.Body.data.fiveStarCount 1 "product five star distribution"
    $adminReviews = Invoke-Api GET (
        "admin/reviews?status=PUBLISHED&rating=5&keyword=" + [Uri]::EscapeDataString($productName)
    ) $null $adminToken
    Assert-Equal $adminReviews.Body.data.total 1 "administrator review search"
    Assert-Equal $adminReviews.Body.data.records[0].id $createdReview.Body.data.id `
        "administrator review detail"
    Assert-Equal @($adminReviews.Body.data.records[0].imageUrls).Count 2 `
        "administrator review images"
    $hiddenReview = Invoke-Api PATCH "admin/reviews/$($createdReview.Body.data.id)/status" @{
        status = "HIDDEN"
    } $adminToken
    Assert-Equal $hiddenReview.Body.data.status "HIDDEN" "administrator hides review"
    $hiddenPublicReviews = Invoke-Api GET "products/$productId/reviews" $null $null
    Assert-Equal $hiddenPublicReviews.Body.data.total 0 "hidden review removed from public list"
    $hiddenReviewSummary = Invoke-Api GET "products/$productId/reviews/summary" $null $null
    Assert-Equal $hiddenReviewSummary.Body.data.total 0 "hidden review removed from summary"
    $restoredReview = Invoke-Api PATCH "admin/reviews/$($createdReview.Body.data.id)/status" @{
        status = "PUBLISHED"
    } $adminToken
    Assert-Equal $restoredReview.Body.data.status "PUBLISHED" "administrator restores review"
    $myReviews = Invoke-Api GET "reviews/mine?current=1&size=10" $null $tokenA
    Assert-Equal $myReviews.Body.data.records[0].orderItemId $orderItemId "my review list"
    Assert-Equal @($myReviews.Body.data.records[0].imageUrls).Count 2 "my review images"
    $foreignReviewList = Invoke-Api GET "reviews/mine" $null $tokenB
    Assert-Equal $foreignReviewList.Body.data.total 0 "my reviews are user scoped"
    $hotProducts = Invoke-Api GET "products/hot-ranking?days=30&limit=20" $null $null
    $hotProduct = @($hotProducts.Body.data | Where-Object {
        [long]$_.productId -eq [long]$productId
    })
    Assert-Equal $hotProduct.Count 1 "completed order appears in hot product ranking"
    Assert-Equal $hotProduct[0].salesQuantity 2 "hot product ranking sales quantity"
    $invalidSalesTrend = Invoke-Api GET "admin/dashboard/sales-trend?days=0" $null $adminToken @(400)
    Assert-Equal $invalidSalesTrend.Body.code 40001 "sales trend day boundary"
    $salesTrend = Invoke-Api GET "admin/dashboard/sales-trend?days=7" $null $adminToken
    Assert-Equal $salesTrend.Body.data.days 7 "sales trend range"
    Assert-Equal @($salesTrend.Body.data.points).Count 7 "sales trend fills missing dates"
    $trendSalesAmount = @($salesTrend.Body.data.points |
        ForEach-Object { [decimal]$_.salesAmount } |
        Measure-Object -Sum).Sum
    Assert-True ([decimal]$trendSalesAmount -ge 24.68) "sales trend includes completed order"
    $topProducts = Invoke-Api GET "admin/dashboard/top-products?limit=20" $null $adminToken
    $topProduct = @($topProducts.Body.data.items | Where-Object {
        [long]$_.productId -eq [long]$productId
    })
    Assert-Equal $topProduct.Count 1 "dashboard top products include completed order"
    Assert-Equal $topProduct[0].soldQuantity 2 "dashboard top product quantity"

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
            -SellerUser $sellerUser `
            -CategoryName $categoryName `
            -ProductName $productName `
            -SkuCode $skuCode `
            -DisabledParentName $disabledParentName
    }
}
