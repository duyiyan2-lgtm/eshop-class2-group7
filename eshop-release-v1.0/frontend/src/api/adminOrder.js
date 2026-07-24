import http from './http'

export const getAdminOrders = (params) => http.get('/admin/orders', { params })

export const getAdminOrder = (id) => http.get(`/admin/orders/${id}`)

export const getAdminOrderLogs = (id) => http.get(`/admin/orders/${id}/logs`)

export const shipAdminOrder = (id) => http.post(`/admin/orders/${id}/ship`)

export const cancelAdminOrder = (id) => http.post(`/admin/orders/${id}/cancel`)
