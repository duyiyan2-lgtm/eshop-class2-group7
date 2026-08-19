<script setup>
import { computed, ref, watch } from 'vue'
import { getProductReviews, getProductReviewSummary } from '../../api/review'
import { formatDateTime, specsText } from '../../utils/shop'

const props = defineProps({
  productId: {
    type: [Number, String],
    required: true,
  },
  pageSize: {
    type: Number,
    default: 6,
  },
})

const reviews = ref([])
const current = ref(1)
const total = ref(0)
const summary = ref(null)
const loading = ref(false)
const summaryLoading = ref(false)
const errorMessage = ref('')
let requestSequence = 0
let summaryRequestSequence = 0

const stars = (rating) => {
  const value = Math.round(Math.max(0, Math.min(5, Number(rating) || 0)))
  return `${'★'.repeat(value)}${'☆'.repeat(5 - value)}`
}

const averageRating = computed(() => Number(summary.value?.averageRating || 0).toFixed(1))

const ratingDistribution = computed(() => {
  const summaryTotal = Number(summary.value?.total) || 0
  return [
    ['5 星', Number(summary.value?.fiveStarCount) || 0],
    ['4 星', Number(summary.value?.fourStarCount) || 0],
    ['3 星', Number(summary.value?.threeStarCount) || 0],
    ['2 星', Number(summary.value?.twoStarCount) || 0],
    ['1 星', Number(summary.value?.oneStarCount) || 0],
  ].map(([label, count]) => ({
    label,
    count,
    percentage: summaryTotal ? Math.round((count / summaryTotal) * 100) : 0,
  }))
})

const loadSummary = async () => {
  const productId = Number(props.productId)
  if (!Number.isInteger(productId) || productId <= 0) return

  const requestId = ++summaryRequestSequence
  summaryLoading.value = true
  try {
    const data = await getProductReviewSummary(productId)
    if (requestId === summaryRequestSequence) summary.value = data
  } catch {
    if (requestId === summaryRequestSequence) summary.value = null
  } finally {
    if (requestId === summaryRequestSequence) summaryLoading.value = false
  }
}

const loadReviews = async (page = current.value) => {
  const productId = Number(props.productId)
  if (!Number.isInteger(productId) || productId <= 0) return

  const requestId = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await getProductReviews(productId, {
      current: page,
      size: props.pageSize,
    })
    if (requestId !== requestSequence) return
    reviews.value = Array.isArray(data?.records) ? data.records : []
    current.value = Number(data?.current) || page
    total.value = Number(data?.total) || 0
  } catch (error) {
    if (requestId !== requestSequence) return
    reviews.value = []
    total.value = 0
    errorMessage.value = error.message || '商品评价加载失败'
  } finally {
    if (requestId === requestSequence) loading.value = false
  }
}

const changePage = (page) => {
  if (loading.value || page < 1 || page > Math.max(1, Math.ceil(total.value / props.pageSize))) return
  void loadReviews(page)
}

const retry = () => {
  void loadSummary()
  void loadReviews(current.value)
}

watch(
  () => props.productId,
  () => {
    reviews.value = []
    current.value = 1
    total.value = 0
    summary.value = null
    requestSequence += 1
    summaryRequestSequence += 1
    void loadSummary()
    void loadReviews(1)
  },
  { immediate: true },
)
</script>

<template>
  <section class="product-reviews" aria-labelledby="product-review-heading">
    <header>
      <div>
        <span class="heading-mark"></span>
        <h2 id="product-review-heading">用户评价</h2>
      </div>
      <small>
        {{ summaryLoading && !summary ? '评分统计中…' : `共 ${summary?.total ?? total} 条真实评价` }}
      </small>
    </header>

    <section v-if="summary && Number(summary.total) > 0" class="review-summary">
      <div class="average-rating">
        <strong>{{ averageRating }}</strong>
        <span class="stars" :aria-label="`平均 ${averageRating} 星`">
          {{ stars(summary.averageRating) }}
        </span>
        <small>综合评分</small>
      </div>
      <div class="rating-distribution">
        <div v-for="item in ratingDistribution" :key="item.label">
          <span>{{ item.label }}</span>
          <i><b :style="{ width: `${item.percentage}%` }"></b></i>
          <small>{{ item.count }} 条</small>
        </div>
      </div>
    </section>

    <div v-if="loading && !reviews.length" class="review-state">评价加载中…</div>
    <div v-else-if="errorMessage" class="review-state error">
      <span>{{ errorMessage }}</span>
      <button type="button" @click="retry">重新加载</button>
    </div>
    <div v-else-if="!reviews.length" class="review-state">该商品暂时还没有评价</div>

    <div v-else class="review-list">
      <article v-for="review in reviews" :key="review.id" class="review-item">
        <div class="review-meta">
          <div>
            <strong>{{ review.reviewerNickname || '匿名用户' }}</strong>
            <span class="stars" :aria-label="`${review.rating} 星评价`">
              {{ stars(review.rating) }}
            </span>
          </div>
          <time>{{ formatDateTime(review.createdAt) }}</time>
        </div>
        <p>{{ review.content }}</p>
        <div v-if="review.imageUrls?.length" class="review-photos">
          <a
            v-for="(url, index) in review.imageUrls"
            :key="`${review.id}-${index}`"
            :href="url"
            target="_blank"
            rel="noopener noreferrer"
          >
            <img :src="url" :alt="`评价图片 ${index + 1}`" />
          </a>
        </div>
        <small v-if="specsText(review.skuSpecs)" class="review-specs">
          购买规格：{{ specsText(review.skuSpecs) }}
        </small>
      </article>
    </div>

    <footer v-if="total > pageSize" class="review-pagination">
      <button
        type="button"
        :disabled="loading || current <= 1"
        @click="changePage(current - 1)"
      >
        上一页
      </button>
      <span>第 {{ current }} / {{ Math.ceil(total / pageSize) }} 页</span>
      <button
        type="button"
        :disabled="loading || current >= Math.ceil(total / pageSize)"
        @click="changePage(current + 1)"
      >
        下一页
      </button>
    </footer>
  </section>
</template>

<style scoped>
.product-reviews {
  margin-top: 18px;
  padding: 24px;
  color: #334155;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, .05);
}

.review-photos {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 8px 0 4px;
}

.review-photos a {
  display: block;
  width: 72px;
  height: 72px;
  overflow: hidden;
  border-radius: 8px;
  background: #f1f5f9;
}

.review-photos img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-reviews > header,
.review-meta,
.review-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.product-reviews > header > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.heading-mark {
  width: 5px;
  height: 22px;
  background: #e1251b;
  border-radius: 999px;
}

h2 {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
}

header small,
.review-meta time,
.review-specs {
  color: #94a3b8;
  font-size: 12px;
}

.review-list {
  margin-top: 14px;
}

.review-summary {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 28px;
  align-items: center;
  margin-top: 18px;
  padding: 20px;
  background: linear-gradient(135deg, #fffbeb, #fff);
  border: 1px solid #fde68a;
  border-radius: 14px;
}

.average-rating {
  display: grid;
  justify-items: center;
  gap: 5px;
  padding-right: 24px;
  border-right: 1px solid #fde68a;
}

.average-rating strong {
  color: #d97706;
  font-size: 38px;
  line-height: 1;
}

.average-rating small {
  color: #92400e;
  font-size: 12px;
}

.rating-distribution {
  display: grid;
  gap: 7px;
}

.rating-distribution > div {
  display: grid;
  grid-template-columns: 38px minmax(80px, 1fr) 45px;
  gap: 10px;
  align-items: center;
  color: #78716c;
  font-size: 12px;
}

.rating-distribution i {
  height: 7px;
  overflow: hidden;
  background: #fef3c7;
  border-radius: 999px;
}

.rating-distribution b {
  display: block;
  height: 100%;
  background: #f59e0b;
  border-radius: inherit;
}

.rating-distribution small {
  color: #a8a29e;
  text-align: right;
}

.review-item {
  padding: 18px 0;
  border-top: 1px solid #eef2f7;
}

.review-meta > div {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-meta strong {
  color: #0f172a;
  font-size: 14px;
}

.stars {
  color: #f59e0b;
  font-size: 17px;
  letter-spacing: 1px;
}

.review-item p {
  margin: 13px 0 9px;
  color: #334155;
  line-height: 1.75;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.review-state {
  margin-top: 14px;
  padding: 34px 16px;
  color: #94a3b8;
  text-align: center;
  background: #f8fafc;
  border-radius: 12px;
}

.review-state.error {
  color: #b91c1c;
  background: #fef2f2;
}

.review-state button,
.review-pagination button {
  padding: 7px 14px;
  color: #e1251b;
  font: inherit;
  background: #fff;
  border: 1px solid #ffd7c2;
  border-radius: 8px;
  cursor: pointer;
}

.review-state button {
  margin-left: 12px;
}

.review-pagination {
  justify-content: center;
  margin-top: 12px;
  padding-top: 18px;
  border-top: 1px solid #eef2f7;
}

.review-pagination span {
  color: #64748b;
  font-size: 13px;
}

.review-pagination button:disabled {
  color: #94a3b8;
  background: #f8fafc;
  border-color: #e2e8f0;
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .product-reviews {
    margin-top: 12px;
    padding: 18px 16px;
    border: 0;
    border-radius: 12px;
    box-shadow: none;
  }

  .product-reviews > header,
  .review-meta {
    align-items: flex-start;
  }

  .review-meta > div {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .review-meta time {
    white-space: nowrap;
  }

  .review-pagination {
    gap: 8px;
  }

  .review-summary {
    grid-template-columns: 1fr;
    gap: 16px;
    padding: 16px;
  }

  .average-rating {
    padding: 0 0 14px;
    border-right: 0;
    border-bottom: 1px solid #fde68a;
  }
}
</style>
