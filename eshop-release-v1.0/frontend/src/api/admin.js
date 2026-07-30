import http from './http'

export const getDashboardSummary = () => http.get('/admin/dashboard/summary')

export const getDashboardSalesTrend = (days = 7) => (
  http.get('/admin/dashboard/sales-trend', { params: { days } })
)

export const getDashboardTopProducts = (limit = 10) => (
  http.get('/admin/dashboard/top-products', { params: { limit } })
)

export const getInventoryAlerts = ({
  current = 1,
  size = 20,
  threshold = 10,
  keyword,
} = {}) => http.get('/admin/inventory/alerts', {
  params: { current, size, threshold, keyword },
})

export const getUsers = ({
  current = 1,
  size = 20,
  keyword,
  role,
  status,
} = {}) => http.get('/admin/users', {
  params: { current, size, keyword, role, status },
})

export const updateUserStatus = (id, status) => (
  http.patch(`/admin/users/${id}/status`, { status })
)

export const getAdminReviews = ({
  current = 1,
  size = 20,
  keyword,
  rating,
  status,
} = {}) => http.get('/admin/reviews', {
  params: { current, size, keyword, rating, status },
})

export const updateAdminReviewStatus = (id, status) => (
  http.patch(`/admin/reviews/${id}/status`, { status })
)
