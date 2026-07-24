<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getAddresses } from '../../api/address'
import { getCart } from '../../api/cart'
import { createOrder } from '../../api/order'
import { formatMoney, specsText, sumMoney } from '../../utils/shop'

const router = useRouter()
const addresses = ref([])
const cartItems = ref([])
const selectedAddressId = ref()
const remark = ref('')
const loading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')

const selectedItems = computed(() => cartItems.value.filter((item) => item.selected))
const validSelectedItems = computed(() => selectedItems.value.filter((item) => item.available))
const hasInvalidSelection = computed(() => selectedItems.value.some((item) => !item.available))
const totalAmount = computed(() => sumMoney(validSelectedItems.value.map((item) => item.subtotal)))
const selectedQuantity = computed(() => (
  validSelectedItems.value.reduce((count, item) => count + item.quantity, 0)
))
const canSubmit = computed(() => (
  Boolean(selectedAddressId.value)
  && validSelectedItems.value.length > 0
  && !hasInvalidSelection.value
))

const loadCheckout = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const [addressData, cartData] = await Promise.all([getAddresses(), getCart()])
    addresses.value = addressData
    cartItems.value = cartData
    const preferred = addressData.find((address) => address.isDefault) || addressData[0]
    selectedAddressId.value = preferred?.id
  } catch (error) {
    errorMessage.value = error.message || '订单信息加载失败'
  } finally {
    loading.value = false
  }
}

const submitOrder = async () => {
  if (!selectedAddressId.value) {
    ElMessage.warning('请选择收货地址')
    return
  }
  if (hasInvalidSelection.value) {
    ElMessage.warning('购物车中存在失效或库存不足的已选商品，请返回购物车处理')
    return
  }
  if (!validSelectedItems.value.length) {
    ElMessage.warning('没有可以结算的商品')
    return
  }
  submitting.value = true
  try {
    const order = await createOrder({
      addressId: selectedAddressId.value,
      remark: remark.value.trim() || null,
    })
    ElMessage.success('订单创建成功，请完成支付')
    await router.replace({ name: 'pc-order-payment', params: { id: order.id } })
  } catch (error) {
    ElMessage.error(error.message || '订单创建失败')
    await loadCheckout()
  } finally {
    submitting.value = false
  }
}

onMounted(loadCheckout)
</script>

<template>
  <div v-loading="loading" class="checkout-page">
    <header class="checkout-heading">
      <div>
        <p>CONFIRM ORDER</p>
        <h1>确认订单</h1>
      </div>
      <ol>
        <li class="done">1 购物车</li>
        <li class="active">2 确认订单</li>
        <li>3 完成支付</li>
      </ol>
    </header>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="checkout-alert"
    />

    <section class="checkout-section">
      <div class="section-heading">
        <div>
          <span>01</span>
          <h2>选择收货地址</h2>
        </div>
        <el-button
          link
          type="primary"
          @click="router.push({ name: 'pc-addresses', query: { redirect: '/pc/checkout' } })"
        >
          管理收货地址
        </el-button>
      </div>

      <div v-if="addresses.length" class="address-options">
        <button
          v-for="address in addresses"
          :key="address.id"
          type="button"
          class="address-option"
          :class="{ active: selectedAddressId === address.id }"
          @click="selectedAddressId = address.id"
        >
          <div>
            <strong>{{ address.receiverName }}</strong>
            <span>{{ address.phone }}</span>
            <el-tag v-if="address.isDefault" size="small">默认</el-tag>
          </div>
          <p>{{ address.province }} {{ address.city }} {{ address.district }} {{ address.detail }}</p>
          <b v-if="selectedAddressId === address.id">✓</b>
        </button>
      </div>
      <el-empty v-else description="请先添加一个收货地址">
        <el-button
          type="primary"
          @click="router.push({ name: 'pc-addresses', query: { redirect: '/pc/checkout' } })"
        >
          新增收货地址
        </el-button>
      </el-empty>
    </section>

    <section class="checkout-section">
      <div class="section-heading">
        <div>
          <span>02</span>
          <h2>商品清单</h2>
        </div>
        <el-button link type="primary" @click="router.push({ name: 'pc-cart' })">返回购物车修改</el-button>
      </div>

      <div v-if="validSelectedItems.length" class="checkout-products">
        <article v-for="item in validSelectedItems" :key="item.id">
          <div class="product-image">
            <img v-if="item.productImage" :src="item.productImage" :alt="item.productName" />
            <span v-else>E-Shop</span>
          </div>
          <div class="product-info">
            <RouterLink :to="`/pc/products/${item.productId}`">{{ item.productName }}</RouterLink>
            <p>{{ specsText(item.specsJson) || '默认规格' }}</p>
          </div>
          <span>{{ formatMoney(item.price) }} × {{ item.quantity }}</span>
          <strong>{{ formatMoney(item.subtotal) }}</strong>
        </article>
      </div>
      <el-empty v-else description="没有已选择的结算商品">
        <el-button type="primary" @click="router.push({ name: 'pc-cart' })">返回购物车</el-button>
      </el-empty>

      <el-alert
        v-if="hasInvalidSelection"
        title="已选商品中存在失效或库存不足项，暂时不能提交订单。"
        type="warning"
        show-icon
        :closable="false"
        class="selection-alert"
      />
    </section>

    <section class="checkout-section">
      <div class="section-heading">
        <div>
          <span>03</span>
          <h2>订单备注</h2>
        </div>
      </div>
      <el-input
        v-model="remark"
        type="textarea"
        :rows="3"
        maxlength="255"
        show-word-limit
        placeholder="选填，可以填写配送或商品相关说明"
      />
    </section>

    <footer class="submit-panel">
      <div>
        <p>共 {{ selectedQuantity }} 件商品</p>
        <span>应付总额由服务端在提交订单时再次校验</span>
      </div>
      <div class="amount">
        <span>应付总额</span>
        <strong>{{ formatMoney(totalAmount) }}</strong>
      </div>
      <el-button
        type="primary"
        size="large"
        :disabled="!canSubmit"
        :loading="submitting"
        @click="submitOrder"
      >
        提交订单
      </el-button>
    </footer>
  </div>
</template>

<style scoped>
.checkout-page { width: min(1120px, 100%); min-height: 500px; margin: 0 auto; padding: 16px 0 56px; }
.checkout-heading { display: flex; align-items: end; justify-content: space-between; margin-bottom: 24px; }
.checkout-heading p { margin: 0 0 6px; color: #2563eb; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.checkout-heading h1 { margin: 0; color: #0f172a; font-size: 32px; }
.checkout-heading ol { display: flex; gap: 26px; margin: 0; padding: 0; color: #94a3b8; font-size: 13px; list-style: none; }
.checkout-heading li.done { color: #64748b; }
.checkout-heading li.active { color: #2563eb; font-weight: 700; }
.checkout-alert { margin-bottom: 18px; }
.checkout-section { margin-bottom: 18px; padding: 28px 32px; background: #fff; border: 1px solid #e2e8f0; border-radius: 18px; box-shadow: 0 10px 32px rgba(15, 23, 42, .05); }
.section-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 22px; }
.section-heading > div { display: flex; align-items: center; gap: 12px; }
.section-heading span { display: grid; width: 32px; height: 32px; place-items: center; color: #2563eb; background: #eff6ff; border-radius: 9px; font-size: 12px; font-weight: 800; }
.section-heading h2 { margin: 0; color: #0f172a; font-size: 20px; }
.address-options { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.address-option { position: relative; padding: 18px; color: #334155; text-align: left; background: #fff; border: 1px solid #cbd5e1; border-radius: 12px; cursor: pointer; font: inherit; }
.address-option.active { background: #f8fbff; border-color: #2563eb; box-shadow: 0 0 0 1px #2563eb; }
.address-option > div { display: flex; align-items: center; gap: 12px; }
.address-option > div span { color: #64748b; }
.address-option p { margin: 14px 28px 0 0; color: #64748b; font-size: 13px; line-height: 1.6; }
.address-option > b { position: absolute; right: 12px; bottom: 10px; color: #2563eb; }
.checkout-products article { display: grid; grid-template-columns: 78px minmax(0, 1fr) 150px 110px; gap: 16px; align-items: center; padding: 16px 0; border-bottom: 1px solid #eef2f7; }
.checkout-products article:last-child { border-bottom: 0; }
.product-image { display: grid; width: 72px; height: 72px; place-items: center; overflow: hidden; color: #93c5fd; background: #eff6ff; border-radius: 10px; font-size: 12px; font-weight: 800; }
.product-image img { width: 100%; height: 100%; object-fit: contain; }
.product-info a { color: #0f172a; font-weight: 700; }
.product-info p { margin: 7px 0 0; color: #64748b; font-size: 13px; }
.checkout-products article > span { color: #64748b; }
.checkout-products article > strong { color: #dc2626; text-align: right; }
.selection-alert { margin-top: 18px; }
.submit-panel { display: flex; align-items: center; gap: 28px; padding: 24px 30px; background: #0f172a; border-radius: 18px; box-shadow: 0 16px 40px rgba(15, 23, 42, .18); }
.submit-panel > div:first-child { margin-right: auto; }
.submit-panel p { margin: 0 0 5px; color: #fff; font-weight: 700; }
.submit-panel div > span { color: #94a3b8; font-size: 12px; }
.submit-panel .amount { display: flex; align-items: baseline; gap: 12px; }
.submit-panel .amount strong { color: #fbbf24; font-size: 30px; }
.submit-panel .el-button { min-width: 150px; }
@media (max-width: 760px) {
  .checkout-heading { align-items: start; flex-direction: column; gap: 18px; }
  .checkout-heading ol { gap: 12px; }
  .address-options { grid-template-columns: 1fr; }
  .checkout-section { padding: 22px 18px; }
  .checkout-products article { grid-template-columns: 64px 1fr; }
  .checkout-products article > span, .checkout-products article > strong { grid-column: 2; text-align: left; }
  .submit-panel { align-items: stretch; flex-direction: column; }
  .submit-panel > div:first-child { margin-right: 0; }
}
</style>
