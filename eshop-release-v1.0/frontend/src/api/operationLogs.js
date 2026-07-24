import http from './http'

export const getOperationLogs = (params) => http.get('/admin/logs', { params })
