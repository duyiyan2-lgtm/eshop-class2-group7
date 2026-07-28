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
  const targets = new Set(
    (orderItemIds || [])
      .map((id) => Number(id))
      .filter((id) => Number.isInteger(id) && id > 0),
  )
  const reviewed = new Set()
  if (!targets.size) return reviewed

  const size = 50
  let current = 1
  while (targets.size && current <= 1000) {
    const page = await getMyReviews({ current, size })
    const records = Array.isArray(page?.records) ? page.records : []
    for (const review of records) {
      const orderItemId = Number(review.orderItemId)
      if (targets.has(orderItemId)) {
        reviewed.add(orderItemId)
        targets.delete(orderItemId)
      }
    }

    const total = Number(page?.total)
    if (!records.length || records.length < size) break
    if (Number.isFinite(total) && current * size >= total) break
    current += 1
  }
  return reviewed
}
