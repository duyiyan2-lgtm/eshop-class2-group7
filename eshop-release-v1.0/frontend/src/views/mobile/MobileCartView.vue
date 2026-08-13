<script setup>
import { computed, onMounted, ref } from 'vue'
import { showConfirmDialog, showSuccessToast, showToast } from 'vant'
import { useRouter } from 'vue-router'
import { getCart, removeCartItem, updateCartItem } from '../../api/cart'
import { notifyCartUpdated } from '../../utils/cartBadge'
import { formatMoney, specsText, sumMoney } from '../../utils/shop'

const router = useRouter()
const items = ref([])
const loading = ref(false)
const refreshing = ref(false)
const bulkUpdating = ref(false)
const manageMode = ref(false)
const errorMessage = ref('')
const updatingIds = ref(new Set())
const failedImages = ref(new Set())

const availableItems = computed(() => items.value.filter((item) => item.available))
const selectedItems = computed(() => (
  items.value.filter((item) => item.selected && item.available)
))
const allSelected = computed(() => (
  availableItems.value.length > 0
  && availableItems.value.every((item) => item.selected)
))
const selectedCount = computed(() => (
  selectedItems.value.reduce((count, item) => count + item.quantity, 0)
))
const totalAmount = computed(() => (
  sumMoney(selectedItems.value.map((item) => item.subtotal))
))
const hasPendingUpdate = computed(() => updatingIds.value.size > 0 || bulkUpdating.value)
const canCheckout = computed(() => (
  selectedItems.value.length > 0
  && !loading.value
  && !hasPendingUpdate.value
  && !errorMessage.value
))

const setItemUpdating = (itemId, updating) => {
  const next = new Set(updatingIds.value)
  if (updating) next.add(itemId)
  else next.delete(itemId)
  updatingIds.value = next
}

const isItemUpdating = (itemId) => updatingIds.value.has(itemId)

const markImageFailed = (itemId) => {
  failedImages.value = new Set([...failedImages.value, itemId])
}

const loadCart = async () => {
  if (loading.value) return false
  loading.value = true
  errorMessage.value = ''
  try {
    items.value = await getCart()
    failedImages.value = new Set()
    return true
  } catch (error) {
    errorMessage.value = error.message || '购物车加载失败'
    return false
  } finally {
    loading.value = false
  }
}

const onRefresh = async () => {
  refreshing.value = true
  const succeeded = await loadCart()
  refreshing.value = false
  if (succeeded) notifyCartUpdated()
  showToast(succeeded ? '已刷新' : { type: 'fail', message: '刷新失败，请重试' })
}

const updateItem = async (item, payload) => {
  if (isItemUpdating(item.id) || bulkUpdating.value) return
  setItemUpdating(item.id, true)
  try {
    const updated = await updateCartItem(item.id, payload)
    const index = items.value.findIndex((entry) => entry.id === item.id)
    if (index >= 0) items.value[index] = updated
    notifyCartUpdated()
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '更新失败' })
    await loadCart()
  } finally {
    setItemUpdating(item.id, false)
  }
}

const changeQuantity = (item, quantity) => {
  if (quantity === item.quantity || isItemUpdating(item.id)) return
  updateItem(item, { quantity })
}

const changeSelected = (item) => {
  if (isItemUpdating(item.id) || bulkUpdating.value) return
  updateItem(item, { selected: !item.selected })
}

const toggleAll = async () => {
  if (hasPendingUpdate.value) return
  const selected = !allSelected.value
  const targets = availableItems.value.filter((item) => item.selected !== selected)
  if (!targets.length) return
  bulkUpdating.value = true
  try {
    await Promise.all(targets.map((item) => updateCartItem(item.id, { selected })))
    await loadCart()
    notifyCartUpdated()
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '全选状态更新失败' })
    await loadCart()
  } finally {
    bulkUpdating.value = false
  }
}

const removeItem = async (item) => {
  if (isItemUpdating(item.id) || bulkUpdating.value) return
  try {
    await showConfirmDialog({
      title: '移出购物车',
      message: `确定移除「${item.productName}」吗？\n移除后仍可重新加入购物车。`,
      messageAlign: 'left',
      confirmButtonText: '确认移除',
      cancelButtonText: '暂不移除',
      confirmButtonColor: '#dc2626',
      cancelButtonColor: '#64748b',
      className: 'eshop-delete-dialog',
      overlayClass: 'eshop-mobile-dialog-overlay',
    })
    setItemUpdating(item.id, true)
    await removeCartItem(item.id)
    items.value = items.value.filter((entry) => entry.id !== item.id)
    notifyCartUpdated()
    showSuccessToast({
      message: '商品已从购物车移除',
      duration: 2000,
      position: 'top',
      className: 'eshop-mobile-toast eshop-mobile-toast--success',
      wordBreak: 'break-word',
      closeOnClick: true,
    })
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      showToast({ type: 'fail', message: error.message || '删除失败' })
    }
  } finally {
    setItemUpdating(item.id, false)
  }
}

const checkout = () => {
  if (!canCheckout.value) {
    showToast({
      type: 'fail',
      message: selectedCount.value ? '请等待购物车更新完成' : '请至少选择一件商品',
    })
    return
  }
  router.push({ name: 'mobile-checkout' })
}

onMounted(loadCart)
</script>

<template>
  <section class="mobile-cart">
    <header class="native-cart-header android-only">
      <div>
        <span>SHOPPING BAG</span>
        <strong>购物车 <small>({{ items.length }})</small></strong>
      </div>
      <button type="button" @click="manageMode = !manageMode">
        {{ manageMode ? '完成' : '管理' }}
      </button>
    </header>

    <nav class="native-cart-tools android-only" aria-label="购物车工具">
      <button type="button" @click="showToast('优惠活动将在结算时自动计算')"><van-icon name="coupon-o" /> 优惠活动</button>
      <button type="button" @click="showToast('库存与价格已为你实时更新')"><van-icon name="discount" /> 价格提醒</button>
      <button type="button" @click="router.push({ name: 'mobile-addresses' })"><van-icon name="location-o" /> 收货地址</button>
    </nav>

    <van-notice-bar
      v-if="errorMessage"
      color="#dc2626"
      background="#fef2f2"
      left-icon="warning-o"
    >
      <span>{{ errorMessage }}</span>
      <button class="notice-action" type="button" @click="loadCart">重试</button>
    </van-notice-bar>

    <div v-if="loading && !items.length" class="cart-loading">
      <van-loading size="24px">加载中...</van-loading>
    </div>

    <van-empty v-else-if="!items.length && !loading" description="购物车还是空的">
      <van-button round type="primary" size="small" @click="router.push({ name: 'mobile-products' })">
        去商城逛逛
      </van-button>
    </van-empty>

    <template v-else>
      <button
        class="cart-addr-bar"
        type="button"
        @click="router.push({ name: 'mobile-addresses' })"
      >
        <van-icon name="location-o" />
        <span>管理收货地址</span>
        <van-icon name="arrow" />
      </button>

      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <div class="cart-list">
          <header class="cart-store-heading">
            <van-checkbox
              :model-value="allSelected"
              :disabled="hasPendingUpdate"
              @click="toggleAll"
            />
            <van-icon name="shop-o" />
            <strong>E-Shop 自营商城</strong>
            <span>官方品质保障</span>
          </header>
          <article
            v-for="item in items"
            :key="item.id"
            class="cart-item"
            :class="{ invalid: !item.available }"
          >
            <van-checkbox
              :model-value="item.selected"
              :disabled="(!item.available && !item.selected) || isItemUpdating(item.id) || bulkUpdating"
              class="item-check"
              @click="changeSelected(item)"
            />

            <div class="item-content">
              <div class="item-top">
                <div class="item-img">
                  <img
                    v-if="item.productImage && !failedImages.has(item.id)"
                    :src="item.productImage"
                    :alt="item.productName"
                    @error="markImageFailed(item.id)"
                  />
                  <img v-else src="/product-placeholder.svg" alt="商品暂无图片" />
                </div>
                <div class="item-info">
                  <div class="item-name">{{ item.productName }}</div>
                  <div class="item-specs">{{ specsText(item.specsJson) || '默认规格' }}</div>
                  <div v-if="!item.available" class="item-warn">
                    <van-tag type="danger" size="mini">
                      {{ item.stock < item.quantity ? `库存仅剩 ${item.stock} 件` : '商品已失效' }}
                    </van-tag>
                  </div>
                </div>
              </div>

              <div class="item-bottom">
                <div class="item-price">{{ formatMoney(item.price) }}</div>
                <van-stepper
                  :model-value="item.quantity"
                  :min="1"
                  :max="Math.max(Math.min(item.stock, 99), 1)"
                  :disabled="!item.available || isItemUpdating(item.id) || bulkUpdating"
                  button-size="24"
                  input-width="36"
                  @change="changeQuantity(item, $event)"
                />
                <van-button
                  icon="delete"
                  size="small"
                  plain
                  hairline
                  class="del-btn"
                  :loading="isItemUpdating(item.id)"
                  :disabled="bulkUpdating"
                  aria-label="删除商品"
                  :class="{ 'manage-visible': manageMode }"
                  @click="removeItem(item)"
                />
              </div>
            </div>
          </article>
        </div>
      </van-pull-refresh>
    </template>

    <van-submit-bar
      v-if="items.length"
      :price="Math.round(Number(totalAmount) * 100)"
      :button-text="`结算 (${selectedCount})`"
      :disabled="!canCheckout"
      :loading="loading || bulkUpdating"
      label="合计"
      safe-area-inset-bottom
      @submit="checkout"
    >
      <van-checkbox
        :model-value="allSelected"
        :disabled="hasPendingUpdate"
        @click="toggleAll"
      >
        全选
      </van-checkbox>
    </van-submit-bar>
  </section>
</template>

<style scoped>
.mobile-cart {
  min-height: 100%;
  padding-bottom: 60px;
  background: #f7f8fa;
}

.native-cart-header {
  align-items: flex-end;
  justify-content: space-between;
  padding: calc(8px + var(--app-safe-top)) 18px 10px;
  color: #f8fafc;
  background: linear-gradient(145deg, #0a1729, #10254a);
}

.native-cart-header > div { display: grid; gap: 2px; }
.native-cart-header span { color: #2563eb; font-size: 9px; font-weight: 900; letter-spacing: .14em; }
.native-cart-header strong { font-size: 25px; line-height: 1.2; }
.native-cart-header small { color: #94a3b8; font-size: 14px; }
.native-cart-header button { padding: 8px 13px; color: #bfdbfe; font: inherit; font-size: 13px; font-weight: 700; background: rgba(37, 99, 235, .14); border: 1px solid rgba(96, 165, 250, .3); border-radius: 999px; }

.native-cart-tools {
  gap: 8px;
  padding: 2px 12px 12px;
  overflow-x: auto;
  background: #10254a;
  scrollbar-width: none;
}

.native-cart-tools button { display: flex; flex: 0 0 auto; align-items: center; gap: 5px; padding: 8px 12px; color: #cbd5e1; font: inherit; font-size: 12px; background: rgba(255, 255, 255, .07); border: 1px solid rgba(147, 197, 253, .18); border-radius: 11px; }
.native-cart-tools :deep(.van-icon) { color: #2563eb; font-size: 16px; }

.notice-action {
  margin-left: 8px;
  padding: 0;
  border: 0;
  color: #2563eb;
  background: transparent;
}

.cart-loading {
  display: flex;
  justify-content: center;
  padding: 80px 0;
}

.cart-list {
  padding: 12px 12px 0;
}

.cart-store-heading {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 13px 14px 4px;
  color: #0f172a;
  background: #fff;
  border-radius: 18px 18px 0 0;
}

.cart-store-heading > :deep(.van-icon) { color: #2563eb; font-size: 18px; }
.cart-store-heading strong { font-size: 14px; }
.cart-store-heading span { margin-left: auto; color: #94a3b8; font-size: 10px; }

.cart-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
  padding: 14px;
  background: #fff;
  border-radius: 12px;
}

.cart-item.invalid {
  background: #fffafa;
  opacity: 0.7;
}

.item-check {
  flex-shrink: 0;
  margin-top: 24px;
}

.item-content,
.item-info {
  flex: 1;
  min-width: 0;
}

.item-top {
  display: flex;
  gap: 12px;
}

.item-img {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  overflow: hidden;
  color: #969799;
  font-size: 11px;
  background: #f5f5f5;
  border-radius: 8px;
}

.item-img img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.item-name {
  display: -webkit-box;
  overflow: hidden;
  color: #323233;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.item-specs {
  margin-top: 4px;
  color: #969799;
  font-size: 12px;
}

.item-warn {
  margin-top: 6px;
}

.item-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f5f5f5;
}

.item-price {
  color: #dc2626;
  font-size: 16px;
  font-weight: 700;
}

.del-btn {
  padding: 4px;
  color: #999;
  font-size: 18px;
  border: none;
}

.cart-addr-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  width: calc(100% - 24px);
  margin: 0 12px 8px;
  padding: 12px 16px;
  color: #323233;
  font-size: 13px;
  text-align: left;
  background: #fff;
  border: 0;
  border-radius: 10px;
}

.cart-addr-bar :last-child {
  margin-left: auto;
  color: #c8c9cc;
}

:deep(.van-submit-bar) {
  bottom: 50px;
}

:deep(.van-submit-bar__checkbox) {
  flex: 1;
}

:global(.is-native-app) .mobile-cart {
  min-height: calc(100dvh - var(--native-tabbar-height));
  background: #07111f;
}

:global(.is-native-app) .mobile-cart :deep(.van-empty) {
  min-height: calc(100dvh - var(--native-tabbar-height) - 148px - var(--app-safe-top));
  justify-content: center;
  padding: 22px 0 60px;
}

:global(.is-native-app) .mobile-cart :deep(.van-empty__description) {
  color: #cbd5e1;
}
</style>
