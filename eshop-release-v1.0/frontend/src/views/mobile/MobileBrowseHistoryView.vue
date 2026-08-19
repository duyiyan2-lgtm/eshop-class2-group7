<script setup>
import { onMounted, ref } from 'vue'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import { useRouter } from 'vue-router'
import {
  clearBrowseHistory,
  getBrowseHistory,
  removeBrowseHistory,
} from '../../api/browseHistory'
import { formatDateTime, formatMoney } from '../../utils/shop'

const router = useRouter()
const records = ref([])
const current = ref(1)
const size = 10
const total = ref(0)
const loading = ref(false)
const refreshing = ref(false)
const clearing = ref(false)
const errorMessage = ref('')
const removingIds = ref(new Set())
const failedImages = ref(new Set())
let requestSequence = 0

const loadHistory = async () => {
  const requestId = ++requestSequence
  loading.value = !refreshing.value
  errorMessage.value = ''
  try {
    const page = await getBrowseHistory({ current: current.value, size })
    if (requestId !== requestSequence) return
    records.value = page.records || []
    total.value = Number(page.total || 0)
    if (!records.value.length && total.value > 0 && current.value > 1) {
      current.value -= 1
      await loadHistory()
    }
  } catch (error) {
    if (requestId === requestSequence) {
      errorMessage.value = error.message || '浏览历史加载失败'
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
  await loadHistory()
}

const changePage = async (page) => {
  current.value = page
  await loadHistory()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const remove = async (product) => {
  try {
    await showConfirmDialog({
      title: '删除记录',
      message: `确定删除“${product.name}”的浏览记录吗？`,
      confirmButtonText: '删除',
    })
  } catch {
    return
  }

  const productId = product.productId
  if (removingIds.value.has(productId)) return
  removingIds.value = new Set(removingIds.value).add(productId)
  try {
    await removeBrowseHistory(productId)
    showSuccessToast('已删除')
    await loadHistory()
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '删除失败' })
  } finally {
    const next = new Set(removingIds.value)
    next.delete(productId)
    removingIds.value = next
  }
}

const clearAll = async () => {
  try {
    await showConfirmDialog({
      title: '清空历史',
      message: '确定清空全部浏览历史吗？',
      confirmButtonText: '清空',
    })
  } catch {
    return
  }

  clearing.value = true
  try {
    await clearBrowseHistory()
    showSuccessToast('已清空')
    current.value = 1
    await loadHistory()
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '清空失败' })
  } finally {
    clearing.value = false
  }
}

const markImageFailed = (productId) => {
  failedImages.value = new Set(failedImages.value).add(productId)
}

onMounted(loadHistory)
</script>

<template>
  <van-pull-refresh v-model="refreshing" class="history-refresh" @refresh="refresh">
    <section class="mobile-history">
      <div class="toolbar">
        <van-button size="small" plain type="danger" :loading="clearing" @click="clearAll">
          清空
        </van-button>
        <van-button size="small" type="primary" @click="router.push({ name: 'mobile-products' })">
          去逛逛
        </van-button>
      </div>

      <van-notice-bar
        v-if="errorMessage"
        color="#dc2626"
        background="#fef2f2"
        left-icon="warning-o"
      >
        <span>{{ errorMessage }}</span>
        <button type="button" class="notice-action" @click="loadHistory">重试</button>
      </van-notice-bar>

      <div v-if="loading" class="state-card">
        <van-loading color="#e1251b">正在加载浏览历史</van-loading>
      </div>

      <van-empty
        v-else-if="!records.length && !errorMessage"
        image="search"
        description="还没有浏览记录"
      >
        <van-button round type="primary" to="/m/products">去逛逛</van-button>
      </van-empty>

      <div v-else class="history-list">
        <article
          v-for="product in records"
          :key="product.productId"
          class="history-card"
          @click="router.push({ name: 'mobile-product-detail', params: { id: product.productId } })"
        >
          <div class="product-image">
            <img
              v-if="product.mainImage && !failedImages.has(product.productId)"
              :src="product.mainImage"
              :alt="product.name"
              @error="markImageFailed(product.productId)"
            />
            <span v-else>E-Shop</span>
          </div>
          <div class="product-info">
            <h2>{{ product.name }}</h2>
            <p>{{ product.subtitle || '品质商品，放心选购' }}</p>
            <div class="product-meta">
              <strong>{{ formatMoney(product.minPrice) }}</strong>
              <span>库存 {{ product.totalStock ?? 0 }}</span>
            </div>
            <small>浏览于 {{ formatDateTime(product.browsedAt) }}</small>
          </div>
          <van-button
            class="remove-button"
            size="small"
            plain
            type="danger"
            :loading="removingIds.has(product.productId)"
            @click.stop="remove(product)"
          >
            删除
          </van-button>
        </article>
      </div>

      <van-pagination
        v-if="total > size"
        class="history-pagination"
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
.history-refresh,
.mobile-history {
  min-height: 100%;
}

.mobile-history {
  box-sizing: border-box;
  padding: 12px 12px 24px;
  background: #f7f8fa;
}

.toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
}

.notice-action {
  margin-left: 8px;
  padding: 0;
  color: #e1251b;
  background: transparent;
  border: 0;
}

.state-card {
  display: grid;
  min-height: 240px;
  place-items: center;
}

.history-list {
  display: grid;
  gap: 12px;
}

.history-card {
  position: relative;
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.product-image {
  display: grid;
  width: 88px;
  height: 88px;
  place-items: center;
  overflow: hidden;
  color: #f5a09a;
  background: #fff1f0;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 800;
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.product-info {
  min-width: 0;
  padding-right: 56px;
}

.product-info h2 {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.35;
}

.product-info p {
  margin: 0 0 8px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}

.product-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.product-meta strong {
  color: #dc2626;
  font-size: 16px;
}

.product-meta span,
.product-info small {
  color: #94a3b8;
  font-size: 12px;
}

.remove-button {
  position: absolute;
  top: 12px;
  right: 12px;
}

.history-pagination {
  margin-top: 16px;
}
</style>
