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

const moneyToCents = (value) => {
  const normalized = String(value ?? 0).trim()
  const match = normalized.match(/^(-?)(\d+)(?:\.(\d{0,2}))?$/)
  if (!match) return 0n
  const fraction = (match[3] || '').padEnd(2, '0')
  const cents = (BigInt(match[2]) * 100n) + BigInt(fraction || '0')
  return match[1] ? -cents : cents
}

export const sumMoney = (values) => {
  const cents = values.reduce((total, value) => total + moneyToCents(value), 0n)
  const sign = cents < 0n ? '-' : ''
  const absolute = cents < 0n ? -cents : cents
  return `${sign}${absolute / 100n}.${String(absolute % 100n).padStart(2, '0')}`
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
