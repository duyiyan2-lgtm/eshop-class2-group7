<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { getFavorites, removeFavorite } from '../../api/favorite'
import { formatDateTime, formatMoney } from '../../utils/shop'

const router = useRouter()
const records = ref([])
const current = ref(1)
const size = ref(12)
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const removingIds = ref(new Set())
const failedImages = ref(new Set())
let requestSequence = 0

const loadFavorites = async () => {
  const requestId = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await getFavorites({ current: current.value, size: size.value })
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
    if (requestId === requestSequence) loading.value = false
  }
}

const changePage = (page) => {
  current.value = page
  loadFavorites()
}

const changeSize = (pageSize) => {
  size.value = pageSize
  current.value = 1
  loadFavorites()
}

const remove = async (product) => {
  try {
    await ElMessageBox.confirm(
      `确定不再收藏“${product.name}”吗？`,
      '取消收藏',
      { type: 'warning', confirmButtonText: '取消收藏', cancelButtonText: '保留' },
    )
  } catch {
    return
  }

  if (removingIds.value.has(product.id)) return
  removingIds.value = new Set(removingIds.value).add(product.id)
  try {
    await removeFavorite(product.id)
    ElMessage.success('已取消收藏')
    await loadFavorites()
  } catch (error) {
    ElMessage.error(error.message || '取消收藏失败')
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
  <section class="favorites-page">
    <header class="page-heading">
      <div>
        <h1>我的收藏</h1>
        <p>保存喜欢的商品，方便随时回来查看。</p>
      </div>
      <el-button type="primary" plain @click="router.push({ name: 'pc-products' })">
        继续逛商城
      </el-button>
    </header>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="error-alert"
    >
      <template #default>
        <el-button link type="primary" @click="loadFavorites">重新加载</el-button>
      </template>
    </el-alert>

    <div v-loading="loading" class="favorites-content">
      <el-empty
        v-if="!loading && !records.length && !errorMessage"
        description="还没有收藏商品"
      >
        <el-button type="primary" @click="router.push({ name: 'pc-products' })">
          去逛逛
        </el-button>
      </el-empty>

      <div v-else class="product-grid">
        <article v-for="product in records" :key="product.id" class="product-card">
          <button
            type="button"
            class="product-image"
            @click="router.push({ name: 'pc-product-detail', params: { id: product.id } })"
          >
            <img
              v-if="product.mainImage && !failedImages.has(product.id)"
              :src="product.mainImage"
              :alt="product.name"
              @error="markImageFailed(product.id)"
            />
            <span v-else>E-Shop</span>
          </button>
          <div class="product-body">
            <h2 @click="router.push({ name: 'pc-product-detail', params: { id: product.id } })">
              {{ product.name }}
            </h2>
            <p>{{ product.subtitle || '品质商品，放心选购' }}</p>
            <div class="price-row">
              <strong>{{ formatMoney(product.minPrice) }}</strong>
              <span>库存 {{ product.totalStock }}</span>
            </div>
            <small>收藏于 {{ formatDateTime(product.favoritedAt) }}</small>
            <div class="card-actions">
              <el-button
                type="primary"
                @click="router.push({ name: 'pc-product-detail', params: { id: product.id } })"
              >
                查看商品
              </el-button>
              <el-button
                type="danger"
                plain
                :loading="removingIds.has(product.id)"
                @click="remove(product)"
              >
                取消收藏
              </el-button>
            </div>
          </div>
        </article>
      </div>
    </div>

    <el-pagination
      v-if="total > 0"
      class="favorites-pagination"
      background
      layout="total, sizes, prev, pager, next"
      :current-page="current"
      :page-size="size"
      :page-sizes="[8, 12, 20]"
      :total="total"
      @current-change="changePage"
      @size-change="changeSize"
    />
  </section>
</template>

<style scoped>
.favorites-page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 12px 0 50px;
}

.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.page-heading span {
  color: #e1251b;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .14em;
}

.page-heading h1 {
  margin: 7px 0 5px;
  color: #0f172a;
  font-size: 34px;
}

.page-heading p {
  margin: 0;
  color: #64748b;
}

.error-alert {
  margin-bottom: 18px;
}

.favorites-content {
  min-height: 300px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.product-card {
  overflow: hidden;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 9px 26px rgba(15, 23, 42, .06);
}

.product-image {
  display: grid;
  width: 100%;
  aspect-ratio: 1.25;
  padding: 0;
  overflow: hidden;
  color: #f5a09a;
  font-size: 24px;
  font-weight: 800;
  place-items: center;
  background: linear-gradient(145deg, #fff1f0, #f8fafc);
  border: 0;
  cursor: pointer;
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.product-body {
  padding: 16px;
}

.product-body h2 {
  display: -webkit-box;
  min-height: 44px;
  margin: 0 0 7px;
  overflow: hidden;
  color: #172033;
  font-size: 16px;
  line-height: 1.4;
  cursor: pointer;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.product-body p {
  margin: 0 0 14px;
  overflow: hidden;
  color: #7b879b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.price-row strong {
  color: #dc2626;
  font-size: 22px;
}

.price-row span,
.product-body small {
  color: #94a3b8;
  font-size: 11px;
}

.product-body small {
  display: block;
  margin-top: 6px;
}

.card-actions {
  display: flex;
  margin-top: 15px;
}

.card-actions .el-button {
  flex: 1;
}

.favorites-pagination {
  justify-content: center;
  margin-top: 28px;
}

@media (max-width: 1000px) {
  .product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
