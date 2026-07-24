import axios from 'axios'

// 等待组长后端 v1.0 接口正式对接后改成 false（联调环境已就绪，但 C 端联调走 docker compose）
const USE_MOCK = true

/**
 * Mock 数据
 *
 * 字段名严格对齐组长后端 OrderResponse（参考 eshop-release-v1.0/backend/src/main/java/com/eshop/backend/order/dto/OrderResponse.java）：
 * - orderNo / status / totalAmount / receiverName / receiverPhone / receiverAddress / remark
 * - paidAt / shippedAt / completedAt / canceledAt / createdAt / updatedAt
 * - items: [{ id, productId, productName, skuSpecs, productImage, price, quantity, subtotal }]
 * - logs: [{ id, fromStatus, toStatus, remark, operatorName, createdAt }]
 */
const mockOrders = [
  {
    id: 1,
    orderNo: 'ORD20260724001',
    status: 'PENDING_PAYMENT',
    totalAmount: 1998.00,
    receiverName: '张三',
    receiverPhone: '13800138000',
    receiverAddress: '广东省深圳市南山区科技园路1号',
    remark: '',
    paidAt: null,
    shippedAt: null,
    completedAt: null,
    canceledAt: null,
    createdAt: '2026-07-24T09:00:00',
    updatedAt: '2026-07-24T09:00:00',
    items: [
      {
        id: 1,
        productId: 101,
        productName: '测试手机',
        skuSpecs: '{"颜色":"黑色","版本":"128G"}',
        productImage: 'https://img.yzcdn.cn/vant/cat.jpeg',
        price: 999.00,
        quantity: 2,
        subtotal: 1998.00,
      },
    ],
    logs: [
      { id: 1, fromStatus: null, toStatus: 'PENDING_PAYMENT', remark: '订单创建', operatorName: '系统', createdAt: '2026-07-24T09:00:00' },
    ],
  },
  {
    id: 2,
    orderNo: 'ORD20260723002',
    status: 'PAID',
    totalAmount: 99.00,
    receiverName: '张三',
    receiverPhone: '13800138000',
    receiverAddress: '广东省深圳市南山区科技园路1号',
    remark: '尽快发货',
    paidAt: '2026-07-23T10:30:00',
    shippedAt: null,
    completedAt: null,
    canceledAt: null,
    createdAt: '2026-07-23T10:00:00',
    updatedAt: '2026-07-23T10:30:00',
    items: [
      {
        id: 2,
        productId: 102,
        productName: '测试T恤',
        skuSpecs: '{"尺码":"L","颜色":"白色"}',
        productImage: 'https://img.yzcdn.cn/vant/cat.jpeg',
        price: 99.00,
        quantity: 1,
        subtotal: 99.00,
      },
    ],
    logs: [
      { id: 2, fromStatus: null, toStatus: 'PENDING_PAYMENT', remark: '订单创建', operatorName: '系统', createdAt: '2026-07-23T10:00:00' },
      { id: 3, fromStatus: 'PENDING_PAYMENT', toStatus: 'PAID', remark: '用户完成支付', operatorName: '张三', createdAt: '2026-07-23T10:30:00' },
    ],
  },
  {
    id: 3,
    orderNo: 'ORD20260722003',
    status: 'SHIPPED',
    totalAmount: 29.00,
    receiverName: '李四',
    receiverPhone: '13900139000',
    receiverAddress: '北京市海淀区中关村大街1号',
    remark: '',
    paidAt: '2026-07-22T15:00:00',
    shippedAt: '2026-07-23T09:00:00',
    completedAt: null,
    canceledAt: null,
    createdAt: '2026-07-22T14:30:00',
    updatedAt: '2026-07-23T09:00:00',
    items: [
      {
        id: 3,
        productId: 103,
        productName: '测试水杯',
        skuSpecs: '{"容量":"500ml","颜色":"蓝色"}',
        productImage: 'https://img.yzcdn.cn/vant/cat.jpeg',
        price: 29.00,
        quantity: 1,
        subtotal: 29.00,
      },
    ],
    logs: [
      { id: 4, fromStatus: null, toStatus: 'PENDING_PAYMENT', remark: '订单创建', operatorName: '系统', createdAt: '2026-07-22T14:30:00' },
      { id: 5, fromStatus: 'PENDING_PAYMENT', toStatus: 'PAID', remark: '用户完成支付', operatorName: '李四', createdAt: '2026-07-22T15:00:00' },
      { id: 6, fromStatus: 'PAID', toStatus: 'SHIPPED', remark: '商家已发货', operatorName: '商家小王', createdAt: '2026-07-23T09:00:00' },
    ],
  },
]

const http = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  config.headers.Authorization = 'Bearer mock-jwt-token-for-eshop-c-module'
  return config
})

const wait = (ms = 300) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * GET /orders?status=&current=&size=
 * 订单分页（按当前用户）
 */
export async function getOrders(params = {}) {
  if (USE_MOCK) {
    await wait()
    let list = [...mockOrders]
    if (params.status) {
      list = list.filter((order) => order.status === params.status)
    }
    const current = Number(params.current) || 1
    const size = Number(params.size) || 10
    const total = list.length
    const start = (current - 1) * size
    const records = list.slice(start, start + size)
    return { records, total, current, size }
  }
  const { data } = await http.get('/orders', { params })
  return data
}

/**
 * GET /orders/{id}
 * 订单详情
 */
export async function getOrder(id) {
  if (USE_MOCK) {
    await wait()
    const order = mockOrders.find((item) => item.id === Number(id))
    if (!order) throw new Error('订单不存在')
    return { ...order, items: [...order.items], logs: [...order.logs] }
  }
  const { data } = await http.get(`/orders/${id}`)
  return data
}

/**
 * GET /orders/{id}/logs
 * 订单状态流转日志
 */
export async function getOrderLogs(id) {
  if (USE_MOCK) {
    await wait()
    const order = mockOrders.find((item) => item.id === Number(id))
    if (!order) throw new Error('订单不存在')
    return [...order.logs]
  }
  const { data } = await http.get(`/orders/${id}/logs`)
  return data
}

/**
 * POST /orders/{id}/pay
 * 模拟支付
 */
export async function payOrder(id) {
  if (USE_MOCK) {
    await wait(500)
    const order = mockOrders.find((item) => item.id === Number(id))
    if (!order) throw new Error('订单不存在')
    if (order.status !== 'PENDING_PAYMENT') throw new Error('当前订单状态不允许支付')
    order.status = 'PAID'
    order.paidAt = new Date().toISOString()
    order.updatedAt = order.paidAt
    order.logs.push({
      id: order.logs.length + 1,
      fromStatus: 'PENDING_PAYMENT',
      toStatus: 'PAID',
      remark: '用户完成模拟支付',
      operatorName: '当前用户',
      createdAt: order.paidAt,
    })
    return {
      orderId: order.id,
      paymentNo: `PAY${Date.now()}`,
      status: 'PAID',
      paidAt: order.paidAt,
    }
  }
  const { data } = await http.post(`/orders/${id}/pay`)
  return data
}

/**
 * POST /orders/{id}/cancel
 * 取消订单
 */
export async function cancelOrder(id) {
  if (USE_MOCK) {
    await wait()
    const order = mockOrders.find((item) => item.id === Number(id))
    if (!order) throw new Error('订单不存在')
    if (order.status !== 'PENDING_PAYMENT') throw new Error('当前订单状态不允许取消')
    order.status = 'CANCELED'
    order.canceledAt = new Date().toISOString()
    order.updatedAt = order.canceledAt
    order.logs.push({
      id: order.logs.length + 1,
      fromStatus: 'PENDING_PAYMENT',
      toStatus: 'CANCELED',
      remark: '用户取消订单',
      operatorName: '当前用户',
      createdAt: order.canceledAt,
    })
    return { orderId: order.id, status: 'CANCELED', canceledAt: order.canceledAt }
  }
  const { data } = await http.post(`/orders/${id}/cancel`)
  return data
}

/**
 * POST /orders/{id}/confirm
 * 确认收货
 */
export async function confirmOrder(id) {
  if (USE_MOCK) {
    await wait()
    const order = mockOrders.find((item) => item.id === Number(id))
    if (!order) throw new Error('订单不存在')
    if (order.status !== 'SHIPPED') throw new Error('当前订单状态不允许确认收货')
    order.status = 'COMPLETED'
    order.completedAt = new Date().toISOString()
    order.updatedAt = order.completedAt
    order.logs.push({
      id: order.logs.length + 1,
      fromStatus: 'SHIPPED',
      toStatus: 'COMPLETED',
      remark: '用户确认收货',
      operatorName: '当前用户',
      createdAt: order.completedAt,
    })
    return { orderId: order.id, status: 'COMPLETED', completedAt: order.completedAt }
  }
  const { data } = await http.post(`/orders/${id}/confirm`)
  return data
}