import http from './http'

export const getFavorites = (params) => http.get('/favorites', { params })

export const getFavoriteStatus = (productId) => http.get(`/favorites/${productId}/status`)

export const addFavorite = (productId) => http.post(`/favorites/${productId}`)

export const removeFavorite = (productId) => http.delete(`/favorites/${productId}`)
