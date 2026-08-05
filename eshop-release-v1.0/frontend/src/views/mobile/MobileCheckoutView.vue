<script setup>
import { computed, ref, watch } from 'vue'
import { showToast } from 'vant'
import { useRoute, useRouter } from 'vue-router'
import { getAddresses } from '../../api/address'
import { getCart } from '../../api/cart'
import { getProduct } from '../../api/catalog'
import { buyNowOrder, createOrder } from '../../api/order'
import { formatMoney, specsText, sumMoney } from '../../utils/shop'

const router = useRouter()
const route = useRoute()
const addresses = ref([])
const cartItems = ref([])
const selectedAddressId = ref()
const remark = ref('')
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const failedImages = ref(new Set())

const isBuyNow = computed(() => route.query.mode === 'buyNow')
const buyNowSelection = computed(() => {
  if (!isBuyNow.value) return null
  const productId = Number(route.query.productId)
  const skuId = Number(route.query.skuId)
  const quantity = Number(route.query.quantity)
  if (!Number.isInteger(productId) || productId <= 0) return null
  if (!Number.isInteger(skuId) || skuId <= 0) return null
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 99) return null
  return { productId, skuId, quantity }
})

const selectedItems = computed(() => cartItems.value.filter((item) => item.selected))
const validSelectedItems = computed(() => selectedItems.value.filter((item) => item.available))
const invalidSelectedItems = computed(() => selectedItems.value.filter((item) => !item.available))
const hasInvalidSelection = computed(() => invalidSelectedItems.value.length > 0)
const totalAmount = computed(() => sumMoney(validSelectedItems.value.map((item) => item.subtotal)))

const totalAmountFen = computed(() => {
  const parts = String(totalAmount.value ?? '0').split('.')
  const whole = BigInt(parts[0] || '0')
  const fraction = BigInt((parts[1] || '00').padEnd(2, '0').slice(0, 2))
  return Number((whole * 100n) + fraction)
})

const selectedQuantity = computed(() => (
  validSelectedItems.value.reduce((count, item) => count + item.quantity, 0)
))

const selectedAddress = computed(() => (
  addresses.value.find((address) => address.id === selectedAddressId.value)
))

const canSubmit = computed(() => (
  Boolean(selectedAddressId.value)
  && validSelectedItems.value.length > 0
  && !hasInvalidSelection.value
  && !loading.value
  && !submitting.value
  && !errorMessage.value
))

const submitButtonText = computed(() => {
  if (!validSelectedItems.value.length) return '请选择商品'
  if (hasInvalidSelection.value) return '请处理失效商品'
  if (!selectedAddressId.value) return '请选择地址'
  return '提交订单'
})

const loadBuyNowItems = async () => {
  const selection = buyNowSelection.value
  if (!selection) throw new Error('立即购买参数无效，请返回商品详情重新选择')
  const product = await getProduct(selection.productId)
  const sku = (product.skus || []).find((item) => item.id === selection.skuId)
  if (!sku) throw new Error('所选商品规格不存在，请重新选择')
  return [{
    id: `buy-now-${sku.id}`,
    productId: product.id,
    skuId: sku.id,
    productName: product.name,
    productImage: product.mainImage,
    specsJson: sku.specsJson,
    price: sku.price,
    quantity: selection.quantity,
    subtotal: sumMoney(Array.from({ length: selection.quantity }, () => sku.price)),
    selected: true,
    available: sku.stock >= selection.quantity,
  }]
}

const loadCheckout = async () => {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''

  try {
    const itemsPromise = isBuyNow.value ? loadBuyNowItems() : getCart()
    const [addressData, cartData] = await Promise.all([getAddresses(), itemsPromise])
    addresses.value = addressData || []
    cartItems.value = cartData || []
    failedImages.value = new Set()

    const selectionStillExists = selectedAddressId.value
      && addresses.value.some((address) => address.id === selectedAddressId.value)
    if (!selectionStillExists) {
      const preferred = addresses.value.find((address) => address.isDefault) || addresses.value[0]
      selectedAddressId.value = preferred?.id
    }
  } catch (error) {
    addresses.value = []
    cartItems.value = []
    selectedAddressId.value = undefined
    errorMessage.value = error.message || '订单信息加载失败'
  } finally {
    loading.value = false
  }
}

const selectAddress = (id) => {
  selectedAddressId.value = id
}

const goAddresses = () => {
  router.push({ name: 'mobile-addresses', query: { redirect: route.fullPath } })
}

const goCart = () => {
  router.push({ name: 'mobile-cart' })
}

const markImageFailed = (itemId) => {
  failedImages.value = new Set([...failedImages.value, itemId])
}

const onSubmit = async () => {
  if (submitting.value || loading.value || errorMessage.value) return
  if (!selectedAddressId.value) {
    showToast('请选择收货地址')
    return
  }
  if (hasInvalidSelection.value) {
    showToast('购物车中有失效或库存不足的商品，请返回购物车处理')
    return
  }
  if (!validSelectedItems.value.length) {
    showToast('没有可以结算的商品')
    return
  }

  submitting.value = true
  try {
    const remarkValue = remark.value.trim() || null
    const selection = buyNowSelection.value
    const order = isBuyNow.value
      ? await buyNowOrder({
          addressId: selectedAddressId.value,
          skuId: selection.skuId,
          quantity: selection.quantity,
          remark: remarkValue,
        })
      : await createOrder({
          addressId: selectedAddressId.value,
          remark: remarkValue,
        })
    showToast({ type: 'success', message: '订单创建成功，请完成支付' })
    await router.replace({ name: 'mobile-order-payment', params: { id: order.id } })
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '订单创建失败' })
    await loadCheckout()
  } finally {
    submitting.value = false
  }
}

watch(() => route.fullPath, loadCheckout, { immediate: true })
</script>

<template>
  <section class="mobile-checkout">
    <van-skeleton v-if="loading" title :row="6" class="block skeleton" />

    <div v-else-if="errorMessage" class="load-error">
      <van-empty image="error" :description="errorMessage">
        <van-button round type="primary" size="small" @click="loadCheckout">
          重新加载
        </van-button>
      </van-empty>
    </div>

    <template v-else>
      <van-cell-group inset title="收货地址" class="block">
        <template v-if="addresses.length">
          <van-cell
            v-for="address in addresses"
            :key="address.id"
            clickable
            center
            :class="{ 'address-active': selectedAddressId === address.id }"
            @click="selectAddress(address.id)"
          >
            <template #title>
              <div class="address-title">
                <strong>{{ address.receiverName }}</strong>
                <span>{{ address.phone }}</span>
                <van-tag v-if="address.isDefault" plain type="primary" size="medium">
                  默认
                </van-tag>
              </div>
            </template>
            <template #label>
              <p class="address-detail">
                {{ address.province }} {{ address.city }} {{ address.district }} {{ address.detail }}
              </p>
            </template>
            <template #right-icon>
              <van-icon
                :name="selectedAddressId === address.id ? 'checked' : 'circle'"
                :color="selectedAddressId === address.id ? '#1d4ed8' : '#c8c9cc'"
                size="20"
              />
            </template>
          </van-cell>
          <van-cell
            is-link
            title="管理收货地址"
            value="新增 / 编辑"
            @click="goAddresses"
          />
        </template>
        <van-empty v-else description="还没有收货地址" class="empty">
          <van-button round type="primary" size="small" @click="goAddresses">
            去添加地址
          </van-button>
        </van-empty>
      </van-cell-group>

      <van-cell-group
        inset
        :title="isBuyNow ? '立即购买商品' : '商品清单'"
        class="block"
      >
        <van-card
          v-for="item in validSelectedItems"
          :key="item.id"
          :title="item.productName"
          :desc="specsText(item.specsJson) || '默认规格'"
          :price="Number(item.price)"
          :num="item.quantity"
          class="checkout-card"
        >
          <template #thumb>
            <div class="thumb">
              <img
                v-if="item.productImage && !failedImages.has(item.id)"
                :src="item.productImage"
                :alt="item.productName"
                @error="markImageFailed(item.id)"
              />
              <span v-else>E-Shop</span>
            </div>
          </template>
          <template #footer>
            <span class="subtotal">小计 {{ formatMoney(item.subtotal) }}</span>
          </template>
        </van-card>

        <van-empty v-if="!validSelectedItems.length" description="还没有选择商品">
          <van-button round type="primary" size="small" @click="goCart">
            返回购物车
          </van-button>
        </van-empty>
      </van-cell-group>

      <van-cell-group
        v-if="invalidSelectedItems.length"
        inset
        title="失效商品"
        class="block invalid-block"
      >
        <van-card
          v-for="item in invalidSelectedItems"
          :key="item.id"
          :title="item.productName"
          desc="已下架或库存不足"
          :price="Number(item.price)"
          :num="item.quantity"
          class="checkout-card invalid"
        >
          <template #thumb>
            <div class="thumb invalid">
              <img
                v-if="item.productImage && !failedImages.has(item.id)"
                :src="item.productImage"
                :alt="item.productName"
                @error="markImageFailed(item.id)"
              />
              <span v-else>E-Shop</span>
            </div>
          </template>
        </van-card>
        <p class="invalid-tip">失效商品无法结算，请返回购物车调整后再提交</p>
      </van-cell-group>

      <van-cell-group inset title="订单备注" class="block">
        <van-field
          v-model="remark"
          type="textarea"
          rows="2"
          autosize
          maxlength="255"
          show-word-limit
          placeholder="选填，可填写配送或商品相关说明"
        />
      </van-cell-group>

      <van-cell-group inset title="费用明细" class="block">
        <van-cell title="商品件数" :value="`共 ${selectedQuantity} 件`" />
        <van-cell title="商品总额" :value="formatMoney(totalAmount)" />
        <van-cell title="应付金额" :value="formatMoney(totalAmount)" class="amount-row" />
        <van-cell
          v-if="selectedAddress"
          title="收货人"
          :value="`${selectedAddress.receiverName} ${selectedAddress.phone}`"
        />
      </van-cell-group>

      <div class="bottom-placeholder" />

      <van-submit-bar
        :price="totalAmountFen"
        :button-text="submitButtonText"
        :disabled="!canSubmit"
        :loading="submitting"
        button-color="#1d4ed8"
        @submit="onSubmit"
      />
    </template>
  </section>
</template>

<style scoped>
.mobile-checkout {
  min-height: 100%;
  padding-bottom: 60px;
  background: #f7f8fa;
}

.load-error {
  display: grid;
  min-height: 60vh;
  place-items: center;
}

.skeleton {
  margin: 12px 16px;
  padding: 16px;
  background: #fff;
  border-radius: 12px;
}

.block {
  margin: 0 0 12px;
}

.block :deep(.van-cell-group__title) {
  padding-left: 16px;
  color: #323233;
  font-weight: 600;
}

.empty {
  padding: 24px 0;
}

.address-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.address-title strong {
  color: #0f172a;
  font-size: 15px;
}

.address-title span {
  color: #64748b;
  font-size: 13px;
}

.address-detail {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.address-active {
  background: #f8fbff;
}

.checkout-card {
  background: #fff;
}

.checkout-card :deep(.van-card__title) {
  font-size: 14px;
  font-weight: 600;
}

.checkout-card :deep(.van-card__desc),
.checkout-card :deep(.van-card__num) {
  color: #969799;
  font-size: 12px;
}

.checkout-card :deep(.van-card__price) {
  color: #dc2626;
  font-weight: 700;
}

.checkout-card .subtotal {
  color: #dc2626;
  font-size: 13px;
  font-weight: 700;
}

.invalid-block {
  border: 1px solid #fecaca;
}

.invalid-block :deep(.van-cell-group__title) {
  color: #b91c1c;
}

.checkout-card.invalid :deep(.van-card__title) {
  color: #94a3b8;
  text-decoration: line-through;
}

.invalid-tip {
  margin: 0;
  padding: 0 16px 12px;
  color: #b91c1c;
  font-size: 12px;
}

.thumb {
  display: grid;
  width: 64px;
  height: 64px;
  overflow: hidden;
  color: #93c5fd;
  background: #eff6ff;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 800;
  place-items: center;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.thumb.invalid {
  opacity: .5;
}

.amount-row :deep(.van-cell__value) {
  color: #dc2626;
  font-size: 16px;
  font-weight: 700;
}

.bottom-placeholder {
  height: 12px;
}
</style>
