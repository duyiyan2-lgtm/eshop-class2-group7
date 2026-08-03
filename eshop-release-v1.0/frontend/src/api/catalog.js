import http from './http'

export const getCategories = () => http.get('/categories')

export const getProducts = (params) => http.get('/products', { params })

export const getProduct = (id) => http.get(`/products/${id}`)

export const getHotProducts = ({ days = 30, limit = 5 } = {}) => (
  http.get('/products/hot-ranking', { params: { days, limit } })
)
