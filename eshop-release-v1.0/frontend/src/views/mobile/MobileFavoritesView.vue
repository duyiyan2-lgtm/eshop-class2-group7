<script setup>
import { onMounted, ref } from 'vue'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import { useRouter } from 'vue-router'
import { getFavorites, removeFavorite } from '../../api/favorite'
import { formatDateTime, formatMoney } from '../../utils/shop'

const router = useRouter()
const records = ref([])
const current = ref(1)
const size = 10
const total = ref(0)
const loading = ref(false)
const refreshing = ref(false)
const errorMessage = ref('')
const removingIds = ref(new Set())
const failedImages = ref(new Set())
let requestSequence = 0

const loadFavorites = async () => {
  const requestId = ++requestSequence
  loading.value = !refreshing.value
  errorMessage.value = ''
  try {
    const page = await getFavorites({ current: current.value, size })
    if (requestId !== requestSequence) return
    records.value = page.records || []
    total.value = Number(page.total || 0)
    if (!records.value.length && total.value > 0 && current.value > 1) {
      current.value -= 1
      await loadFavorites()
    }
  } catch (error) {
    if (requestId === requestSequence) {
      errorMessage.value = error.message || '收藏列表加载失败'
    }
  } finally {
    if (requestId === requestSequence) {
      loading.value = false
      refreshing.value = false
    }
  }
}

const refresh = async () => {
  refreshing.value = true
  current.value = 1
  await loadFavorites()
}

const changePage = async (page) => {
  current.value = page
  await loadFavorites()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const remove = async (product) => {
  try {
    await showConfirmDialog({
      title: '取消收藏',
      message: `确定不再收藏“${product.name}”吗？`,
      confirmButtonText: '取消收藏',
    })
  } catch {
    return
  }

  if (removingIds.value.has(product.id)) return
  removingIds.value = new Set(removingIds.value).add(product.id)
  try {
    await removeFavorite(product.id)
    showSuccessToast('已取消收藏')
    await loadFavorites()
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '取消收藏失败' })
  } finally {
    const next = new Set(removingIds.value)
    next.delete(product.id)
    removingIds.value = next
  }
}

const markImageFailed = (productId) => {
  failedImages.value = new Set(failedImages.value).add(productId)
}

onMounted(loadFavorites)
</script>

<template>
  <van-pull-refresh v-model="refreshing" class="favorites-refresh" @refresh="refresh">
    <section class="mobile-favorites">
      <van-notice-bar
        v-if="errorMessage"
        color="#dc2626"
        background="#fef2f2"
        left-icon="warning-o"
      >
        <span>{{ errorMessage }}</span>
        <button type="button" class="notice-action" @click="loadFavorites">重试</button>
      </van-notice-bar>

      <div v-if="loading" class="state-card">
        <van-loading color="#2563eb">正在加载收藏商品</van-loading>
      </div>

      <van-empty
        v-else-if="!records.length && !errorMessage"
        image="search"
        description="还没有收藏商品"
      >
        <van-button round type="primary" to="/m/products">去逛逛</van-button>
      </van-empty>

      <div v-else class="favorite-list">
        <article
          v-for="product in records"
          :key="product.id"
          class="favorite-card"
          @click="router.push({ name: 'mobile-product-detail', params: { id: product.id } })"
        >
          <div class="product-image">
            <img
              v-if="product.mainImage && !failedImages.has(product.id)"
              :src="product.mainImage"
              :alt="product.name"
              @error="markImageFailed(product.id)"
            />
            <span v-else>E-Shop</span>
          </div>
          <div class="product-info">
            <h2>{{ product.name }}</h2>
            <p>{{ product.subtitle || '品质商品，放心选购' }}</p>
            <div class="product-meta">
              <strong>{{ formatMoney(product.minPrice) }}</strong>
              <span>库存 {{ product.totalStock }}</span>
            </div>
            <small>收藏于 {{ formatDateTime(product.favoritedAt) }}</small>
          </div>
          <van-button
            class="remove-button"
            size="small"
            plain
            type="danger"
            :loading="removingIds.has(product.id)"
            @click.stop="remove(product)"
          >
            取消
          </van-button>
        </article>
      </div>

      <van-pagination
        v-if="total > size"
        class="favorites-pagination"
        :model-value="current"
        :total-items="total"
        :items-per-page="size"
        :show-page-size="3"
        force-ellipses
        @change="changePage"
      />
    </section>
  </van-pull-refresh>
</template>

<style scoped>
.favorites-refresh,
.mobile-favorites {
  min-height: 100%;
}

.mobile-favorites {
  box-sizing: border-box;
  padding: 12px 12px 24px;
  background: #f7f8fa;
}

.notice-action {
  margin-left: 8px;
  padding: 0;
  color: #2563eb;
  background: transparent;
  border: 0;
}

.state-card {
  display: grid;
  min-height: 240px;
  place-items: center;
}

.favorite-list {
  display: grid;
  gap: 12px;
}

.favorite-card {
  position: relative;
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 5px 18px rgba(15, 23, 42, .06);
}

.product-image {
  display: grid;
  width: 96px;
  height: 96px;
  overflow: hidden;
  color: #93c5fd;
  font-weight: 800;
  place-items: center;
  background: linear-gradient(145deg, #eff6ff, #f8fafc);
  border-radius: 10px;
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.product-info {
  min-width: 0;
  padding-right: 44px;
}

.product-info h2 {
  display: -webkit-box;
  margin: 1px 0 5px;
  overflow: hidden;
  color: #172033;
  font-size: 15px;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.product-info p {
  margin: 0 0 9px;
  overflow: hidden;
  color: #8791a5;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-meta {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.product-meta strong {
  color: #ee0a24;
  font-size: 18px;
}

.product-meta span,
.product-info small {
  color: #94a3b8;
  font-size: 11px;
}

.product-info small {
  display: block;
  margin-top: 5px;
}

.remove-button {
  position: absolute;
  top: 12px;
  right: 10px;
}

.favorites-pagination {
  margin-top: 18px;
}
</style>
