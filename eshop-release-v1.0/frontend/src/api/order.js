import http from './http'

export const createOrder = (payload) => http.post('/orders', payload)

export const getOrders = (params) => http.get('/orders', { params })

export const getOrder = (id) => http.get(`/orders/${id}`)

export const getOrderLogs = (id) => http.get(`/orders/${id}/logs`)

export const payOrder = (id) => http.post(`/orders/${id}/pay`)

export const cancelOrder = (id) => http.post(`/orders/${id}/cancel`)

export const confirmOrder = (id) => http.post(`/orders/${id}/confirm`)
