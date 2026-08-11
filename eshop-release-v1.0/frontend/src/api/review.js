import http from './http'

export const getProductReviews = (productId, params) => (
  http.get(`/products/${productId}/reviews`, { params })
)

export const getProductReviewSummary = (productId) => (
  http.get(`/products/${productId}/reviews/summary`)
)

export const createReview = (payload) => http.post('/reviews', payload)

export const getMyReviews = (params) => http.get('/reviews/mine', { params })

export const getReviewedOrderItemIds = async (orderItemIds) => {
  const targets = [...new Set(
    (orderItemIds || [])
      .map((id) => Number(id))
      .filter((id) => Number.isInteger(id) && id > 0),
  )]
  if (!targets.length) return new Set()

  const requests = []
  for (let index = 0; index < targets.length; index += 100) {
    requests.push(http.get('/reviews/mine/order-items', {
      params: { orderItemIds: targets.slice(index, index + 100).join(',') },
    }))
  }
  const results = await Promise.all(requests)
  return new Set(results.flat().map((id) => Number(id)).filter(Number.isInteger))
}
