<script setup>
import { computed, ref, watch } from 'vue'
import { showImagePreview, showSuccessToast, showToast } from 'vant'
import { useRoute, useRouter } from 'vue-router'
import { addCartItem, getCart } from '../../api/cart'
import { getProduct } from '../../api/catalog'
import { recordBrowseHistory } from '../../api/browseHistory'
import { addFavorite, getFavoriteStatus, removeFavorite } from '../../api/favorite'
import ProductReviewList from '../../components/review/ProductReviewList.vue'
import { useAuthStore } from '../../stores/auth'
import { notifyCartUpdated } from '../../utils/cartBadge'
import { formatMoney, parseSpecs } from '../../utils/shop'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const product = ref(null)
const selectedSkuId = ref(null)
const quantity = ref(1)
const loading = ref(false)
const adding = ref(false)
const imageFailed = ref(false)
const errorMessage = ref('')
const favorited = ref(false)
const favoriteLoading = ref(false)
const cartCount = ref(0)
let favoriteRequestSequence = 0
let cartRequestSequence = 0

const selectedSku = computed(() => {
  if (!product.value || selectedSkuId.value === null) return null
  return (product.value.skus || []).find((sku) => sku.id === selectedSkuId.value) || null
})

const maxQuantity = computed(() => Math.min(selectedSku.value?.stock || 0, 99))

const cartBadge = computed(() => {
  if (cartCount.value <= 0) return ''
  return cartCount.value > 99 ? '99+' : String(cartCount.value)
})

const canAddToCart = computed(() => (
  Boolean(selectedSku.value && selectedSku.value.stock > 0)
  && quantity.value >= 1
  && quantity.value <= maxQuantity.value
))

const galleryImages = computed(() => {
  if (!product.value?.mainImage || imageFailed.value) return []
  return [product.value.mainImage]
})

const loadCartCount = async () => {
  const requestId = ++cartRequestSequence
  if (!auth.isLoggedIn) {
    cartCount.value = 0
    return
  }

  try {
    const items = await getCart()
    if (requestId !== cartRequestSequence || !auth.isLoggedIn) return
    cartCount.value = (Array.isArray(items) ? items : []).reduce(
      (total, item) => total + Math.max(0, Number(item.quantity) || 0),
      0,
    )
  } catch {
    if (requestId === cartRequestSequence) cartCount.value = 0
  }
}

const skuLabel = (sku) => parseSpecs(sku.specsJson)
  .map((item) => item.value)
  .join(' / ')

let requestSequence = 0

const loadProduct = async () => {
  const requestId = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  imageFailed.value = false
  product.value = null
  selectedSkuId.value = null
  quantity.value = 1
  favorited.value = false
  favoriteLoading.value = false
  favoriteRequestSequence += 1

  try {
    const id = Number(route.params.id)
    if (!Number.isInteger(id) || id <= 0) throw new Error('商品编号不正确')
    const data = await getProduct(id)
    if (requestId !== requestSequence) return
    product.value = data
    const skus = data.skus || []
    const firstAvailable = skus.find((sku) => sku.stock > 0) || skus[0]
    selectedSkuId.value = firstAvailable ? firstAvailable.id : null
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
    // 收藏状态失败不影响商品浏览，登录失效由公共 HTTP 层统一处理。
  }
}

const toggleFavorite = async () => {
  if (!auth.isLoggedIn) {
    showToast('请先登录后再收藏')
    await router.push({
      name: 'mobile-login',
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
      showSuccessToast('已收藏')
    } else {
      await removeFavorite(productId)
      showSuccessToast('已取消收藏')
    }
  } catch (error) {
    if (product.value?.id === productId) favorited.value = previous
    showToast({ type: 'fail', message: error.message || '收藏操作失败' })
  } finally {
    if (product.value?.id === productId) favoriteLoading.value = false
  }
}

const selectSku = (sku) => {
  if (!sku || sku.stock <= 0) {
    showToast('该规格暂时无货')
    return
  }
  selectedSkuId.value = sku.id
  quantity.value = Math.max(1, Math.min(quantity.value, Math.min(sku.stock, 99)))
}

const onQuantityChange = (value) => {
  if (value < 1) {
    quantity.value = 1
    return
  }
  if (value > maxQuantity.value) {
    quantity.value = maxQuantity.value
    showToast(`最多只能购买 ${maxQuantity.value} 件`)
  }
}

const previewImage = (index) => {
  if (!galleryImages.value.length) return
  showImagePreview({
    images: galleryImages.value,
    startPosition: index,
    closeable: true,
  })
}

const addToCart = async () => {
  if (!auth.isLoggedIn) {
    showToast('请先登录后再加入购物车')
    await router.push({
      name: 'mobile-login',
      query: { redirect: route.fullPath },
    })
    return
  }
  if (!selectedSku.value) {
    showToast('请先选择商品规格')
    return
  }
  if (selectedSku.value.stock <= 0) {
    showToast('当前规格暂时无货')
    return
  }
  if (quantity.value < 1 || quantity.value > selectedSku.value.stock) {
    showToast(`购买数量必须在 1 至 ${selectedSku.value.stock} 之间`)
    return
  }

  adding.value = true
  try {
    const item = await addCartItem({
      skuId: selectedSku.value.id,
      quantity: quantity.value,
    })
    notifyCartUpdated()
    await loadCartCount()
    showSuccessToast({
      message: `已加入购物车，当前数量 ${item.quantity}`,
      duration: 2200,
      position: 'top',
      className: 'eshop-mobile-toast eshop-mobile-toast--success',
      wordBreak: 'break-word',
      closeOnClick: true,
    })
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '加入购物车失败' })
  } finally {
    adding.value = false
  }
}

const goCart = () => {
  router.push({ name: 'mobile-cart' })
}

const buyNow = async () => {
  if (!selectedSku.value) {
    showToast('请先选择商品规格')
    return
  }
  if (!canAddToCart.value) {
    showToast(selectedSku.value.stock <= 0 ? '当前规格暂时无货' : '购买数量不正确')
    return
  }

  const checkoutLocation = {
    name: 'mobile-checkout',
    query: {
      mode: 'buyNow',
      productId: String(product.value.id),
      skuId: String(selectedSku.value.id),
      quantity: String(quantity.value),
    },
  }
  if (!auth.isLoggedIn) {
    showToast('请先登录后再立即购买')
    await router.push({
      name: 'mobile-login',
      query: { redirect: router.resolve(checkoutLocation).fullPath },
    })
    return
  }
  await router.push(checkoutLocation)
}

watch(() => route.params.id, loadProduct, { immediate: true })
watch(() => auth.token, () => { void loadCartCount() }, { immediate: true })
</script>

<template>
  <section class="mobile-detail">
    <div v-if="errorMessage" class="state-block">
      <van-empty :description="errorMessage" image="error">
        <van-button round type="primary" size="small" @click="loadProduct">
          重新加载
        </van-button>
      </van-empty>
    </div>

    <template v-else-if="product">
      <div v-if="galleryImages.length" class="gallery">
        <van-swipe :autoplay="4000" indicator-color="#1d4ed8">
          <van-swipe-item
            v-for="(image, index) in galleryImages"
            :key="image"
            @click="previewImage(index)"
          >
            <img
              :src="image"
              :alt="product.name"
              @error="imageFailed = true"
            />
          </van-swipe-item>
        </van-swipe>
        <button
          type="button"
          class="favorite-button"
          :class="{ active: favorited }"
          :disabled="favoriteLoading"
          :aria-label="favorited ? '取消收藏' : '收藏商品'"
          @click.stop="toggleFavorite"
        >
          <van-loading v-if="favoriteLoading" size="18" color="#ee0a24" />
          <van-icon v-else :name="favorited ? 'like' : 'like-o'" size="22" />
          <span>{{ favorited ? '已收藏' : '收藏' }}</span>
        </button>
        <p>点击图片可放大查看</p>
      </div>
      <div v-else class="gallery placeholder">
        <button
          type="button"
          class="favorite-button favorite-button--placeholder"
          :class="{ active: favorited }"
          :disabled="favoriteLoading"
          :aria-label="favorited ? '取消收藏' : '收藏商品'"
          @click.stop="toggleFavorite"
        >
          <van-loading v-if="favoriteLoading" size="18" color="#ee0a24" />
          <van-icon v-else :name="favorited ? 'like' : 'like-o'" size="22" />
          <span>{{ favorited ? '已收藏' : '收藏' }}</span>
        </button>
        暂无商品图片
      </div>

      <div class="card">
        <div class="price-panel">
          <strong>{{ formatMoney(selectedSku?.price) }}</strong>
          <small v-if="selectedSku">库存 {{ selectedSku.stock }} 件</small>
        </div>
        <h1>{{ product.name }}</h1>
        <p>{{ product.subtitle || '品质商品，放心选购' }}</p>
        <van-tag v-if="product.categoryName" type="primary" plain>
          {{ product.categoryName }}
        </van-tag>
      </div>

      <div class="card">
        <div class="card-heading">
          <span>选择规格</span>
          <small v-if="!product.skus?.length">暂无规格</small>
        </div>
        <div v-if="product.skus?.length" class="sku-list">
          <button
            v-for="sku in product.skus"
            :key="sku.id"
            type="button"
            class="sku-option"
            :class="{ active: selectedSkuId === sku.id, disabled: sku.stock <= 0 }"
            :disabled="sku.stock <= 0"
            @click="selectSku(sku)"
          >
            <span>{{ skuLabel(sku) || '默认规格' }}</span>
            <small>
              {{ sku.stock > 0
                ? `${formatMoney(sku.price)} · 库存 ${sku.stock}`
                : '暂时无货' }}
            </small>
          </button>
        </div>
        <div v-if="selectedSku" class="selected-specs">
          <span v-for="item in parseSpecs(selectedSku.specsJson)" :key="item.key">
            <b>{{ item.key }}</b>{{ item.value }}
          </span>
        </div>
      </div>

      <div class="card">
        <div class="card-heading">
          <span>购买数量</span>
          <small v-if="selectedSku">最多 {{ maxQuantity }} 件</small>
        </div>
        <div class="quantity-row">
          <van-stepper
            v-model="quantity"
            :min="1"
            :max="Math.max(maxQuantity, 1)"
            :disabled="!selectedSku || selectedSku.stock <= 0"
            integer
            @change="onQuantityChange"
          />
          <span v-if="!selectedSku">请先选择规格</span>
          <span v-else-if="selectedSku.stock <= 0">暂时无货</span>
        </div>
      </div>

      <div class="card">
        <div class="card-heading"><span>商品详情</span></div>
        <p class="detail-text">{{ product.detail || '暂无更多商品详情。' }}</p>
      </div>

      <ProductReviewList :product-id="product.id" :page-size="5" />

      <div class="service-row">
        <span>✓ 模拟支付</span>
        <span>✓ 库存实时校验</span>
        <span>✓ 订单状态可追踪</span>
      </div>

      <div class="action-bar">
        <van-button
          type="warning"
          plain
          hairline
          class="cart-button"
          @click="goCart"
        >
          <van-badge :content="cartBadge" :show-zero="false">
            <van-icon name="cart-o" size="19" />
          </van-badge>
          <span>购物车</span>
        </van-button>
        <van-button
          type="warning"
          class="add-button"
          :loading="adding"
          :disabled="!canAddToCart"
          @click="addToCart"
        >
          {{ canAddToCart ? '加入购物车' : (selectedSku ? '暂时无货' : '请选择规格') }}
        </van-button>
        <van-button
          type="danger"
          class="buy-button"
          :disabled="!canAddToCart"
          @click="buyNow"
        >
          立即购买
        </van-button>
      </div>
    </template>

    <div v-else class="state-block">
      <van-loading color="#1d4ed8" size="32" />
      <p>商品加载中…</p>
    </div>
  </section>
</template>

<style scoped>
.mobile-detail {
  min-height: 100%;
  padding: 12px 12px 96px;
  background: #f7f8fa;
}

.state-block {
  display: grid;
  min-height: 60vh;
  place-items: center;
  color: #64748b;
}

.state-block p {
  margin-top: 12px;
}

.gallery {
  position: relative;
  display: grid;
  overflow: hidden;
  background: #fff;
  border-radius: 12px;
}

.gallery :deep(.van-swipe) {
  aspect-ratio: 1;
}

.gallery :deep(.van-swipe-item) {
  display: grid;
  background: linear-gradient(145deg, #eff6ff, #f8fafc);
  place-items: center;
}

.gallery img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.gallery > p {
  margin: 8px 12px;
  color: #94a3b8;
  font-size: 11px;
  text-align: center;
}

.gallery.placeholder {
  aspect-ratio: 1;
  color: #94a3b8;
  font-size: 13px;
  place-items: center;
}

.favorite-button {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 7px 12px;
  color: #475569;
  background: rgba(255, 255, 255, .94);
  border: 0;
  border-radius: 999px;
  box-shadow: 0 2px 10px rgba(15, 23, 42, .12);
}

.favorite-button.active {
  color: #ee0a24;
}

.favorite-button:disabled {
  opacity: .72;
}

.favorite-button span {
  font-size: 12px;
  font-weight: 600;
}

.favorite-button--placeholder {
  position: static;
  margin: 0 auto 12px;
}

.card {
  margin-top: 12px;
  padding: 16px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, .04);
}

.price-panel {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}

.price-panel strong {
  color: #dc2626;
  font-size: 26px;
}

.price-panel small,
.card-heading small {
  color: #94a3b8;
  font-size: 12px;
}

.card h1 {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 18px;
  line-height: 1.4;
}

.card > p {
  margin: 0 0 8px;
  color: #64748b;
  font-size: 13px;
}

.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 600;
}

.sku-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.sku-option {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  color: #334155;
  text-align: left;
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
}

.sku-option.active {
  color: #1d4ed8;
  background: #eff6ff;
  border-color: #1d4ed8;
  box-shadow: 0 0 0 1px #1d4ed8;
}

.sku-option.disabled {
  color: #94a3b8;
  background: #f8fafc;
}

.sku-option small {
  color: #64748b;
  font-size: 11px;
}

.selected-specs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.selected-specs span {
  display: flex;
  gap: 4px;
  padding: 4px 8px;
  color: #475569;
  background: #f1f5f9;
  border-radius: 6px;
  font-size: 11px;
}

.selected-specs b {
  color: #1e3a8a;
}

.quantity-row {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #94a3b8;
  font-size: 12px;
}

.card .detail-text {
  margin: 0;
  color: #475569;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-word;
}

.service-row {
  display: flex;
  justify-content: center;
  gap: 14px;
  margin-top: 16px;
  color: #94a3b8;
  font-size: 11px;
}

.action-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 10;
  display: flex;
  gap: 8px;
  padding: 8px 12px calc(8px + var(--app-safe-bottom));
  background: #fff;
  border-top: 1px solid #e2e8f0;
  box-shadow: 0 -4px 18px rgba(15, 23, 42, .06);
}

.cart-button {
  flex: 0 0 82px;
}

.cart-button :deep(.van-button__content) {
  gap: 6px;
}

.cart-button :deep(.van-badge__wrapper) {
  display: inline-flex;
}

.add-button {
  flex: 1;
}

.buy-button {
  flex: 1;
}
</style>
