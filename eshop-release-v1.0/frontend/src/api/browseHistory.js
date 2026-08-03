import http from './http'

export const recordBrowseHistory = (productId) => http.post(`/browse-history/${productId}`)

export const getBrowseHistory = (params) => http.get('/browse-history', { params })

export const removeBrowseHistory = (productId) => http.delete(`/browse-history/${productId}`)

export const clearBrowseHistory = () => http.delete('/browse-history')
