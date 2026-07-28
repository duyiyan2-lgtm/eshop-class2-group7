<script setup>
import { ref } from 'vue'
import { getMyReviews } from '../../api/review'
import { formatDateTime, specsText } from '../../utils/shop'

const props = defineProps({
  platform: {
    type: String,
    default: 'pc',
    validator: (value) => ['pc', 'mobile'].includes(value),
  },
  pageSize: {
    type: Number,
    default: 10,
  },
})

const reviews = ref([])
const current = ref(1)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const failedImages = ref(new Set())
let requestSequence = 0

const stars = (rating) => {
  const value = Math.max(0, Math.min(5, Number(rating) || 0))
  return `${'★'.repeat(value)}${'☆'.repeat(5 - value)}`
}

const productTarget = (review) => (
  props.platform === 'mobile'
    ? { name: 'mobile-product-detail', params: { id: review.productId } }
    : { name: 'pc-product-detail', params: { id: review.productId } }
)

const markImageFailed = (id) => {
  failedImages.value = new Set([...failedImages.value, id])
}

const loadReviews = async (page = 1) => {
  const requestId = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await getMyReviews({ current: page, size: props.pageSize })
    if (requestId !== requestSequence) return
    reviews.value = Array.isArray(data?.records) ? data.records : []
    current.value = Number(data?.current) || page
    total.value = Number(data?.total) || 0
    failedImages.value = new Set()
  } catch (error) {
    if (requestId !== requestSequence) return
    reviews.value = []
    total.value = 0
    errorMessage.value = error.message || '我的评价加载失败'
  } finally {
    if (requestId === requestSequence) loading.value = false
  }
}

const changePage = (page) => {
  if (loading.value || page < 1 || page > Math.max(1, Math.ceil(total.value / props.pageSize))) return
  void loadReviews(page)
}

void loadReviews(1)
</script>

<template>
  <div class="history-panel" :class="{ mobile: platform === 'mobile' }">
    <div v-if="loading && !reviews.length" class="history-state">评价加载中…</div>
    <div v-else-if="errorMessage" class="history-state error">
      <p>{{ errorMessage }}</p>
      <button type="button" @click="loadReviews(current)">重新加载</button>
    </div>
    <div v-else-if="!reviews.length" class="history-state">
      <p>你还没有发表过商品评价</p>
      <RouterLink :to="platform === 'mobile' ? '/m/orders' : '/pc/orders'">
        查看已完成订单
      </RouterLink>
    </div>

    <div v-else class="history-list">
      <article v-for="review in reviews" :key="review.id" class="history-item">
        <RouterLink :to="productTarget(review)" class="product-image">
          <img
            v-if="review.productImage && !failedImages.has(review.id)"
            :src="review.productImage"
            :alt="review.productName"
            @error="markImageFailed(review.id)"
          />
          <span v-else>E-Shop</span>
        </RouterLink>
        <div class="review-content">
          <div class="review-heading">
            <div>
              <RouterLink :to="productTarget(review)">{{ review.productName }}</RouterLink>
              <span class="stars">{{ stars(review.rating) }}</span>
            </div>
            <time>{{ formatDateTime(review.createdAt) }}</time>
          </div>
          <small>{{ specsText(review.skuSpecs) || '默认规格' }}</small>
          <p>{{ review.content }}</p>
          <footer>
            <span>订单编号：{{ review.orderId }}</span>
            <span>状态：{{ review.status === 'PUBLISHED' ? '已发布' : review.status }}</span>
          </footer>
        </div>
      </article>
    </div>

    <nav v-if="total > pageSize" class="history-pagination" aria-label="我的评价分页">
      <button
        type="button"
        :disabled="loading || current <= 1"
        @click="changePage(current - 1)"
      >
        上一页
      </button>
      <span>{{ current }} / {{ Math.ceil(total / pageSize) }}</span>
      <button
        type="button"
        :disabled="loading || current >= Math.ceil(total / pageSize)"
        @click="changePage(current + 1)"
      >
        下一页
      </button>
    </nav>
  </div>
</template>

<style scoped>
.history-panel {
  min-height: 300px;
}

.history-list {
  display: grid;
  gap: 14px;
}

.history-item {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 18px;
  padding: 20px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, .04);
}

.product-image {
  display: grid;
  width: 88px;
  height: 88px;
  place-items: center;
  overflow: hidden;
  color: #93c5fd;
  background: #eff6ff;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 800;
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.review-content {
  min-width: 0;
}

.review-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.review-heading > div {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-heading a {
  color: #0f172a;
  font-weight: 700;
}

.review-heading time,
.review-content > small,
.review-content footer {
  color: #94a3b8;
  font-size: 12px;
}

.stars {
  color: #f59e0b;
  font-size: 17px;
  white-space: nowrap;
}

.review-content > p {
  margin: 12px 0;
  color: #334155;
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.review-content footer {
  display: flex;
  gap: 18px;
}

.history-state {
  padding: 60px 20px;
  color: #64748b;
  text-align: center;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
}

.history-state.error {
  color: #b91c1c;
  background: #fef2f2;
}

.history-state a,
.history-state button,
.history-pagination button {
  display: inline-block;
  margin-top: 8px;
  padding: 8px 15px;
  color: #2563eb;
  font: inherit;
  background: #fff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  cursor: pointer;
}

.history-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 18px;
}

.history-pagination button {
  margin-top: 0;
}

.history-pagination button:disabled {
  color: #94a3b8;
  border-color: #e2e8f0;
  cursor: not-allowed;
}

.history-pagination span {
  color: #64748b;
  font-size: 13px;
}

.history-panel.mobile .history-item {
  grid-template-columns: 68px minmax(0, 1fr);
  gap: 12px;
  padding: 15px;
  border: 0;
  border-radius: 12px;
  box-shadow: none;
}

.history-panel.mobile .product-image {
  width: 68px;
  height: 68px;
}

.history-panel.mobile .review-heading,
.history-panel.mobile .review-heading > div,
.history-panel.mobile .review-content footer {
  align-items: flex-start;
  flex-direction: column;
  gap: 4px;
}

.history-panel.mobile .review-content > p {
  margin: 10px 0;
  font-size: 14px;
}

@media (max-width: 640px) {
  .history-item {
    grid-template-columns: 68px minmax(0, 1fr);
    gap: 12px;
    padding: 15px;
  }

  .product-image {
    width: 68px;
    height: 68px;
  }

  .review-heading,
  .review-heading > div {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
