/**
 * 通用工具函数（仿照组长 eshop-release-v1.0/frontend/src/utils/shop.js）
 * 用于订单模块订单状态展示、金额格式化、日期格式化、商品规格解析。
 */

export const ORDER_STATUS = {
  PENDING_PAYMENT: { label: '待支付', type: 'warning' },
  PAID: { label: '待发货', type: 'primary' },
  SHIPPED: { label: '待收货', type: 'success' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELED: { label: '已取消', type: 'info' },
}

export const formatMoney = (value) => {
  const number = Number(value)
  return Number.isFinite(number) ? `¥${number.toFixed(2)}` : '¥0.00'
}

export const formatDateTime = (value) => {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ')
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
}

export const parseSpecs = (specsJson) => {
  if (!specsJson) return []
  try {
    const parsed = JSON.parse(specsJson)
    return Object.entries(parsed).map(([key, value]) => ({ key, value }))
  } catch {
    return [{ key: '规格', value: specsJson }]
  }
}

export const specsText = (specsJson) => parseSpecs(specsJson)
  .map(({ key, value }) => `${key}：${value}`)
  .join('；')

export const orderStatusInfo = (status) => (
  ORDER_STATUS[status] || { label: status || '未知状态', type: 'info' }
)