# C 模块 - 订单支付接口设计

> 负责人：吴松青（组员4）
> 模块：订单支付包
> 路由：/api/orders（前端 PC + H5 + 后台共用）

---

## 一、数据表（待架构确认后建）

### `orders`（订单主表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| order_no | VARCHAR(32) UNIQUE | 订单号 |
| user_id | BIGINT | 用户 ID |
| status | VARCHAR(20) | 订单状态 |
| original_amount | DECIMAL(10,2) | 商品总金额 |
| discount_amount | DECIMAL(10,2) | 优惠金额 |
| payable_amount | DECIMAL(10,2) | 实付金额 |
| address_snapshot | TEXT | 收货地址快照 |
| expire_at | DATETIME | 过期时间（C03 用） |
| created_at | DATETIME | 下单时间 |
| updated_at | DATETIME | 更新时间 |

### `order_item`（订单明细）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| order_id | BIGINT | 订单 ID |
| product_id | BIGINT | 商品 ID |
| product_name | VARCHAR(100) | 商品名（快照） |
| product_image | VARCHAR(255) | 商品图（快照） |
| unit_price | DECIMAL(10,2) | 单价（快照） |
| quantity | INT | 数量 |
| subtotal_amount | DECIMAL(10,2) | 小计金额 |

### `payment_record`（支付记录）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | |
| order_id | BIGINT | |
| channel | VARCHAR(20) | MOCK（模拟支付） |
| trade_no | VARCHAR(64) UNIQUE | 交易号（幂等） |
| amount | DECIMAL(10,2) | |
| status | VARCHAR(20) | SUCCESS / FAILED |
| paid_at | DATETIME | |

---

## 二、订单状态机

```
PENDING_PAYMENT（待支付）
  ├─ 支付成功 → PAID（已支付）→ SHIPPED（已发货）→ COMPLETED（已完成）
  └─ 用户取消 / 超时取消 → CANCELLED（已取消）
```

---

## 三、API 接口清单（8 个）

### 用户端（PC + H5）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/orders` | GET | 订单列表（按状态过滤，分页） |
| `/api/orders/{id}` | GET | 订单详情 |
| `/api/orders` | POST | 创建订单（核心） |
| `/api/orders/{id}/pay` | POST | 模拟支付（核心） |
| `/api/orders/{id}/cancel` | POST | 取消订单 |

### 后台管理端

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/admin/orders` | GET | 订单列表 |
| `/api/admin/orders/{id}` | GET | 订单详情 |
| `/api/admin/orders/{id}/ship` | POST | 发货 |

---

## 四、接口详细设计

### 1. `GET /api/orders` 订单列表

**请求参数**：
```
?status=PENDING_PAYMENT  # 可选：状态过滤
&page=1                  # 可选：页码，默认 1
&pageSize=10             # 可选：每页条数，默认 10
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1,
        "orderNo": "ORD20260724001",
        "status": "PENDING_PAYMENT",
        "payableAmount": 1998.00,
        "createdAt": "2026-07-24 09:00:00",
        "items": [
          {
            "id": 1,
            "productName": "测试手机",
            "productImage": "https://...",
            "unitPrice": 999.00,
            "quantity": 2,
            "subtotalAmount": 1998.00
          }
        ]
      }
    ],
    "total": 25,
    "page": 1,
    "pageSize": 10
  }
}
```

### 2. `GET /api/orders/{id}` 订单详情

**响应**：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "orderNo": "ORD20260724001",
    "status": "PENDING_PAYMENT",
    "payableAmount": 1998.00,
    "originalAmount": 1998.00,
    "discountAmount": 0,
    "expireAt": "2026-07-24 09:30:00",
    "createdAt": "2026-07-24 09:00:00",
    "address": {
      "receiver": "张三",
      "phone": "13800138000",
      "detail": "广东省深圳市南山区科技园路1号"
    },
    "items": [...]
  }
}
```

### 3. `POST /api/orders` 创建订单

**请求体**：
```json
{
  "addressId": 1,
  "cartItemIds": [1, 2]
}
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "orderId": 1,
    "orderNo": "ORD20260724001",
    "payableAmount": 1998.00
  }
}
```

**错误码**：
- 400：参数错误、库存不足、购物车为空
- 401：未登录

### 4. `POST /api/orders/{id}/pay` 模拟支付

**请求体**：`{}`

**响应**：
```json
{
  "code": 200,
  "data": {
    "status": "PAID"
  }
}
```

**关键逻辑**：状态条件更新
```sql
UPDATE orders SET status = 'PAID', updated_at = NOW()
WHERE id = #{orderId} AND user_id = #{userId} AND status = 'PENDING_PAYMENT'
```
返回影响行数为 0 → 订单状态已变更，支付失败。

### 5. `POST /api/orders/{id}/cancel` 取消订单

类似支付，用状态条件更新 `WHERE status = 'PENDING_PAYMENT'`。

只有取消成功才回补库存。

### 6. C03：订单超时自动取消

定时任务每分钟扫描：
```sql
SELECT * FROM orders
WHERE status = 'PENDING_PAYMENT'
  AND expire_at < NOW()
```

对每个超时订单执行条件取消 + 回补库存。

---

## 五、C 模块依赖其他模块

| 依赖 | 模块 | 我的用法 |
|------|------|---------|
| user 表 | A | user_id 外键 |
| JWT 鉴权 | A | 从 token 取 user_id |
| product 表 | B | 创建订单时查商品信息、扣库存 |
| user_address 表 | D | 创建订单时快照地址 |
| cart_item 表 | D | 创建订单时取购物车项 |

详细依赖图见 `D:\eshop-notes\C-模块依赖图.md`。

---

## 六、待架构确认的事项

1. 鉴权方式：JWT 怎么传？（`Authorization: Bearer xxx`？）
2. 统一响应格式：是否 `{code, message, data}`？
3. 错误码规范：401/403/404/500 怎么用？
4. user 表结构（外键关联用）
5. product 表结构（库存字段名、状态枚举值）