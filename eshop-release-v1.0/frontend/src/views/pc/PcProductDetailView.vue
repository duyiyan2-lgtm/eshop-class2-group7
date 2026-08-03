<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { addCartItem } from '../../api/cart'
import { getProduct } from '../../api/catalog'
import { recordBrowseHistory } from '../../api/browseHistory'
import { addFavorite, getFavoriteStatus, removeFavorite } from '../../api/favorite'
import ProductReviewList from '../../components/review/ProductReviewList.vue'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const product = ref(null)
const selectedSkuId = ref()
const quantity = ref(1)
const loading = ref(false)
const adding = ref(false)
const imageFailed = ref(false)
const errorMessage = ref('')
const favorited = ref(false)
const favoriteLoading = ref(false)
let requestSequence = 0
let favoriteRequestSequence = 0

const selectedSku = computed(() => (
  product.value?.skus.find((sku) => sku.id === selectedSkuId.value) || null
))

const maxQuantity = computed(() => Math.min(selectedSku.value?.stock || 0, 99))
const canAddToCart = computed(() => Boolean(selectedSku.value && selectedSku.value.stock > 0))

const formatMoney = (value) => `¥${Number(value || 0).toFixed(2)}`

const parseSpecs = (specsJson) => {
  try {
    const parsed = JSON.parse(specsJson)
    return Object.entries(parsed).map(([key, value]) => ({ key, value }))
  } catch {
    return [{ key: '规格', value: specsJson }]
  }
}

const skuLabel = (sku) => parseSpecs(sku.specsJson)
  .map((item) => item.value)
  .join(' / ')

const loadProduct = async () => {
  const requestId = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  imageFailed.value = false
  product.value = null
  favorited.value = false
  favoriteLoading.value = false
  favoriteRequestSequence += 1
  try {
    const id = Number(route.params.id)
    if (!Number.isInteger(id) || id <= 0) throw new Error('商品编号不正确')
    const data = await getProduct(id)
    if (requestId !== requestSequence) return
    product.value = data
    const firstAvailable = data.skus.find((sku) => sku.stock > 0) || data.skus[0]
    selectedSkuId.value = firstAvailable?.id
    quantity.value = 1
    if (auth.isLoggedIn) {
      void loadFavoriteStatus(data.id)
      void recordBrowseQuietly(data.id)
    }
  } catch (error) {
    if (requestId === requestSequence) {
      errorMessage.value = error.message || '商品详情加载失败'
    }
  } finally {
    if (requestId === requestSequence) loading.value = false
  }
}

const recordBrowseQuietly = async (productId) => {
  try {
    await recordBrowseHistory(productId)
  } catch {
    // 浏览历史失败不打断商品浏览。
  }
}

const loadFavoriteStatus = async (productId) => {
  const requestId = ++favoriteRequestSequence
  try {
    const data = await getFavoriteStatus(productId)
    if (requestId === favoriteRequestSequence && product.value?.id === productId) {
      favorited.value = Boolean(data?.favorited)
    }
  } catch {
    // 收藏状态加载失败不阻塞商品详情。
  }
}

const toggleFavorite = async () => {
  if (!auth.isLoggedIn) {
    ElMessage.info('请先登录后再收藏')
    await router.push({
      name: 'pc-login',
      query: { redirect: route.fullPath },
    })
    return
  }
  const productId = product.value?.id
  if (!productId || favoriteLoading.value) return

  const previous = favorited.value
  favoriteLoading.value = true
  favorited.value = !previous
  try {
    if (favorited.value) {
      await addFavorite(productId)
      ElMessage.success('已收藏')
    } else {
      await removeFavorite(productId)
      ElMessage.success('已取消收藏')
    }
  } catch (error) {
    if (product.value?.id === productId) favorited.value = previous
    ElMessage.error(error.message || '收藏操作失败')
  } finally {
    if (product.value?.id === productId) favoriteLoading.value = false
  }
}

const selectSku = (sku) => {
  selectedSkuId.value = sku.id
  quantity.value = 1
}

const addToCart = async () => {
  if (!auth.isLoggedIn) {
    ElMessage.info('请先登录后再加入购物车')
    await router.push({
      name: 'pc-login',
      query: { redirect: route.fullPath },
    })
    return
  }
  if (!canAddToCart.value) {
    ElMessage.warning('当前规格暂时无货')
    return
  }
  adding.value = true
  try {
    const item = await addCartItem({
      skuId: selectedSku.value.id,
      quantity: quantity.value,
    })
    ElMessage({
      type: 'success',
      message: `已加入购物车，当前数量 ${item.quantity}`,
      duration: 2400,
      showClose: true,
      grouping: true,
    })
  } catch (error) {
    ElMessage.error(error.message || '加入购物车失败')
  } finally {
    adding.value = false
  }
}

watch(() => route.params.id, loadProduct, { immediate: true })
</script>

<template>
  <div class="detail-page">
    <el-breadcrumb separator="›" class="detail-breadcrumb">
      <el-breadcrumb-item :to="{ name: 'pc-products' }">全部商品</el-breadcrumb-item>
      <el-breadcrumb-item v-if="product">{{ product.categoryName }}</el-breadcrumb-item>
      <el-breadcrumb-item v-if="product">{{ product.name }}</el-breadcrumb-item>
    </el-breadcrumb>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button link type="primary" @click="router.push({ name: 'pc-products' })">
          返回商品列表
        </el-button>
      </template>
    </el-alert>

    <section v-loading="loading" class="detail-card">
      <template v-if="product">
        <div class="gallery">
          <div class="main-image">
            <img
              v-if="product.mainImage && !imageFailed"
              :src="product.mainImage"
              :alt="product.name"
              @error="imageFailed = true"
            />
            <div v-else class="image-placeholder">E-Shop</div>
          </div>
          <p>商品图片仅作展示，实际规格以右侧选择为准</p>
        </div>

        <div class="product-info">
          <div class="product-heading">
            <div class="heading-actions">
              <el-tag type="primary" effect="light">{{ product.categoryName || '精选商品' }}</el-tag>
              <el-button
                :type="favorited ? 'danger' : 'default'"
                :plain="!favorited"
                :loading="favoriteLoading"
                @click="toggleFavorite"
              >
                {{ favorited ? '已收藏' : '收藏商品' }}
              </el-button>
            </div>
            <h1>{{ product.name }}</h1>
            <p>{{ product.subtitle || '品质商品，放心选购' }}</p>
          </div>

          <div class="price-panel">
            <span>商城价</span>
            <strong>{{ formatMoney(selectedSku?.price) }}</strong>
            <small v-if="selectedSku">库存 {{ selectedSku.stock }} 件</small>
          </div>

          <div class="option-block">
            <span class="option-label">选择规格</span>
            <div v-if="product.skus.length" class="sku-list">
              <button
                v-for="sku in product.skus"
                :key="sku.id"
                type="button"
                class="sku-option"
                :class="{ active: selectedSkuId === sku.id, disabled: sku.stock <= 0 }"
                @click="selectSku(sku)"
              >
                <span>{{ skuLabel(sku) }}</span>
                <small>{{ sku.stock > 0 ? `${formatMoney(sku.price)} · 库存 ${sku.stock}` : '暂时无货' }}</small>
              </button>
            </div>
            <el-empty v-else description="该商品暂无可用规格" :image-size="70" />
          </div>

          <div v-if="selectedSku" class="selected-specs">
            <span
              v-for="item in parseSpecs(selectedSku.specsJson)"
              :key="item.key"
            >
              <b>{{ item.key }}</b>{{ item.value }}
            </span>
          </div>

          <div class="purchase-row">
            <span class="option-label">购买数量</span>
            <el-input-number
              v-model="quantity"
              :min="1"
              :max="Math.max(maxQuantity, 1)"
              :disabled="!canAddToCart"
            />
            <el-button
              type="primary"
              size="large"
              :loading="adding"
              :disabled="!canAddToCart"
              @click="addToCart"
            >
              {{ canAddToCart ? '加入购物车' : '暂时无货' }}
            </el-button>
          </div>

          <div class="service-row">
            <span>✓ 模拟支付</span>
            <span>✓ 库存实时校验</span>
            <span>✓ 订单状态可追踪</span>
          </div>
        </div>
      </template>
    </section>

    <section v-if="product" class="description-card">
      <div class="description-heading">
        <span></span>
        <h2>商品详情</h2>
      </div>
      <p>{{ product.detail || '暂无更多商品详情。' }}</p>
    </section>

    <ProductReviewList v-if="product" :product-id="product.id" />
  </div>
</template>

<style scoped>
.detail-page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 10px 0 52px;
}

.detail-breadcrumb {
  margin: 8px 0 20px;
}

.detail-card {
  display: grid;
  grid-template-columns: minmax(360px, .9fr) minmax(480px, 1.1fr);
  gap: 52px;
  min-height: 520px;
  padding: 38px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 22px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, .08);
}

.gallery {
  min-width: 0;
}

.main-image {
  display: grid;
  width: 100%;
  aspect-ratio: 1;
  place-items: center;
  overflow: hidden;
  background:
    radial-gradient(circle at 80% 15%, rgba(191, 219, 254, .7), transparent 32%),
    linear-gradient(145deg, #eff6ff, #f8fafc);
  border-radius: 18px;
}

.main-image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.image-placeholder {
  color: #93c5fd;
  font-size: 34px;
  font-weight: 800;
}

.gallery > p {
  margin: 13px 0 0;
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
}

.product-info {
  min-width: 0;
}

.heading-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.product-heading h1 {
  margin: 13px 0 10px;
  color: #0f172a;
  font-size: clamp(28px, 4vw, 40px);
  letter-spacing: -.035em;
  line-height: 1.25;
}

.product-heading p {
  margin: 0;
  color: #64748b;
  line-height: 1.7;
}

.price-panel {
  display: flex;
  align-items: baseline;
  gap: 14px;
  margin: 25px 0;
  padding: 18px 20px;
  background: linear-gradient(90deg, #fff1f2, #fff7ed);
  border-radius: 12px;
}

.price-panel > span {
  color: #64748b;
  font-size: 13px;
}

.price-panel strong {
  color: #dc2626;
  font-size: 32px;
}

.price-panel small {
  margin-left: auto;
  color: #64748b;
}

.option-block {
  display: grid;
  gap: 12px;
}

.option-label {
  color: #475569;
  font-size: 14px;
  font-weight: 700;
}

.sku-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.sku-option {
  display: grid;
  gap: 5px;
  padding: 13px 15px;
  color: #334155;
  text-align: left;
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  cursor: pointer;
  transition: .18s ease;
}

.sku-option:hover,
.sku-option.active {
  color: #1d4ed8;
  background: #eff6ff;
  border-color: #2563eb;
  box-shadow: 0 0 0 1px #2563eb;
}

.sku-option.disabled {
  color: #94a3b8;
  background: #f8fafc;
}

.sku-option small {
  color: #64748b;
}

.selected-specs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.selected-specs span {
  display: flex;
  gap: 6px;
  padding: 6px 9px;
  color: #475569;
  background: #f1f5f9;
  border-radius: 7px;
  font-size: 12px;
}

.selected-specs b {
  color: #1e3a8a;
}

.purchase-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 26px;
  padding-top: 24px;
  border-top: 1px solid #e2e8f0;
}

.purchase-row .el-button {
  min-width: 150px;
}

.service-row {
  display: flex;
  gap: 18px;
  margin-top: 22px;
  color: #64748b;
  font-size: 12px;
}

.description-card {
  margin-top: 22px;
  padding: 30px 38px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
}

.description-heading {
  display: flex;
  align-items: center;
  gap: 10px;
}

.description-heading span {
  width: 4px;
  height: 22px;
  background: #2563eb;
  border-radius: 99px;
}

.description-heading h2 {
  margin: 0;
  font-size: 21px;
}

.description-card p {
  margin: 18px 0 0;
  color: #475569;
  line-height: 1.9;
  white-space: pre-wrap;
}

@media (max-width: 900px) {
  .detail-card {
    grid-template-columns: 1fr;
  }

  .main-image {
    max-height: 480px;
  }
}

@media (max-width: 640px) {
  .detail-card {
    gap: 26px;
    padding: 20px;
  }

  .sku-list {
    grid-template-columns: 1fr;
  }

  .purchase-row {
    align-items: stretch;
    flex-direction: column;
  }

  .service-row {
    flex-wrap: wrap;
  }
}
</style>
