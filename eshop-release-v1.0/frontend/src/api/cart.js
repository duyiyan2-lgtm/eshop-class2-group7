import http from './http'

export const getCart = () => http.get('/cart')

export const addCartItem = (payload) => http.post('/cart', payload)

export const updateCartItem = (itemId, payload) => http.patch(`/cart/${itemId}`, payload)

export const removeCartItem = (itemId) => http.delete(`/cart/${itemId}`)
