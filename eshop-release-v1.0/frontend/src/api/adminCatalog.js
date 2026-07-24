import http from './http'

export const getAdminCategories = () => http.get('/admin/categories')

export const createAdminCategory = (payload) => http.post('/admin/categories', payload)

export const updateAdminCategory = (id, payload) => http.put(`/admin/categories/${id}`, payload)

export const updateAdminCategoryStatus = (id, status) => (
  http.patch(`/admin/categories/${id}/status`, { status })
)

export const removeAdminCategory = (id) => http.delete(`/admin/categories/${id}`)

export const getAdminProducts = (params) => http.get('/admin/products', { params })

export const getAdminProduct = (id) => http.get(`/admin/products/${id}`)

export const createAdminProduct = (payload) => http.post('/admin/products', payload)

export const updateAdminProduct = (id, payload) => http.put(`/admin/products/${id}`, payload)

export const updateAdminProductStatus = (id, status) => (
  http.patch(`/admin/products/${id}/status`, { status })
)

export const removeAdminProduct = (id) => http.delete(`/admin/products/${id}`)

export const createAdminSku = (productId, payload) => (
  http.post(`/admin/products/${productId}/skus`, payload)
)

export const updateAdminSku = (id, payload) => http.put(`/admin/skus/${id}`, payload)

export const updateAdminSkuStock = (id, stock) => http.patch(`/admin/skus/${id}/stock`, { stock })

export const removeAdminSku = (id) => http.delete(`/admin/skus/${id}`)

export const uploadAdminImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  return http.post('/admin/files/upload', form)
}
