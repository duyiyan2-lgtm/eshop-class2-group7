import axios from 'axios'

// 等待后端接口出来后改成 false
const USE_MOCK = true

// 假数据：3 个订单，覆盖各种状态
const mockOrders = [
  {
    id: 1,
    orderNo: 'ORD20260724001',
    status: 'PENDING_PAYMENT',
    payableAmount: 1998.00,
    originalAmount: 1998.00,
    discountAmount: 0,
    expireAt: new Date(Date.now() + 30 * 60 * 1000).toISOString().slice(0, 19).replace('T', ' '),
    createdAt: '2026-07-24 09:00:00',
    address: {
      receiver: '张三',
      phone: '13800138000',
      detail: '广东省深圳市南山区科技园路1号'
    },
    items: [
      {
        id: 1,
        productName: '测试手机',
        unitPrice: 999.00,
        quantity: 2,
        subtotalAmount: 1998.00,
        productImage: 'https://img.yzcdn.cn/vant/cat.jpeg'
      }
    ]
  },
  {
    id: 2,
    orderNo: 'ORD20260723002',
    status: 'PAID',
    payableAmount: 99.00,
    originalAmount: 99.00,
    discountAmount: 0,
    createdAt: '2026-07-23 10:00:00',
    address: {
      receiver: '张三',
      phone: '13800138000',
      detail: '广东省深圳市南山区科技园路1号'
    },
    items: [
      {
        id: 2,
        productName: '测试T恤',
        unitPrice: 99.00,
        quantity: 1,
        subtotalAmount: 99.00,
        productImage: 'https://img.yzcdn.cn/vant/cat.jpeg'
      }
    ]
  },
  {
    id: 3,
    orderNo: 'ORD20260722003',
    status: 'SHIPPED',
    payableAmount: 29.00,
    originalAmount: 29.00,
    discountAmount: 0,
    createdAt: '2026-07-22 14:30:00',
    address: {
      receiver: '李四',
      phone: '13900139000',
      detail: '北京市海淀区中关村大街1号'
    },
    items: [
      {
        id: 3,
        productName: '测试水杯',
        unitPrice: 29.00,
        quantity: 1,
        subtotalAmount: 29.00,
        productImage: 'https://img.yzcdn.cn/vant/cat.jpeg'
      }
    ]
  }
]

const request = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000
})

// 请求拦截器：自动加 JWT
request.interceptors.request.use((config) => {
  config.headers.Authorization = 'Bearer mock-jwt-token-12345'
  return config
})

/**
 * 获取我的订单列表
 * @param {Object} params - { status?: string, page?: number, pageSize?: number }
 */
export async function getMyOrders(params = {}) {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 300)) // 模拟网络延迟
    let list = mockOrders
    if (params.status) {
      list = list.filter(o => o.status === params.status)
    }
    return { code: 200, data: { list, total: list.length } }
  }
  return request.get('/orders', { params })
}

/**
 * 获取订单详情
 * @param {string|number} id - 订单 ID
 */
export async function getOrderDetail(id) {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 300))
    const order = mockOrders.find(o => o.id === Number(id))
    if (!order) return { code: 404, message: '订单不存在' }
    return { code: 200, data: order }
  }
  return request.get(`/orders/${id}`)
}

/**
 * 取消订单
 * @param {string|number} id - 订单 ID
 */
export async function cancelOrder(id) {
  if (USE_MOCK) {
    await new Promise(r => setTimeout(r, 300))
    const order = mockOrders.find(o => o.id === Number(id))
    if (!order) return { code: 404, message: '订单不存在' }
    if (order.status !== 'PENDING_PAYMENT') {
      return { code: 400, message: '订单状态不允许取消' }
    }
    order.status = 'CANCELLED'
    return { code: 200, message: '已取消' }
  }
  return request.post(`/orders/${id}/cancel`)
}

// 状态文案
export function statusText(status) {
  const map = {
    PENDING_PAYMENT: '待支付',
    PAID: '已支付',
    SHIPPED: '已发货',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }
  return map[status] || status
}

// 状态对应的 Vant Tag 类型
export function statusType(status) {
  const map = {
    PENDING_PAYMENT: 'warning',
    PAID: 'primary',
    SHIPPED: 'success',
    COMPLETED: 'success',
    CANCELLED: 'default'
  }
  return map[status] || 'default'
}