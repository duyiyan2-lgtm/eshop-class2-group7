import http from './http'

export const getCategories = () => http.get('/categories')

export const getProducts = (params) => http.get('/products', { params })

export const getProduct = (id) => http.get(`/products/${id}`)
