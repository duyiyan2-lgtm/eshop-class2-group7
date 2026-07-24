<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { getCart, removeCartItem, updateCartItem } from '../../api/cart'
import { formatMoney, specsText, sumMoney } from '../../utils/shop'

const router = useRouter()
const items = ref([])
const loading = ref(false)
const updatingId = ref()
const errorMessage = ref('')

const availableItems = computed(() => items.value.filter((item) => item.available))
const selectedItems = computed(() => items.value.filter((item) => item.selected && item.available))
const hasInvalidSelection = computed(() => items.value.some((item) => item.selected && !item.available))
const allSelected = computed(() => (
  availableItems.value.length > 0
  && availableItems.value.every((item) => item.selected)
))
const selectedCount = computed(() => (
  selectedItems.value.reduce((count, item) => count + item.quantity, 0)
))
const totalAmount = computed(() => sumMoney(selectedItems.value.map((item) => item.subtotal)))
const canCheckout = computed(() => selectedItems.value.length > 0 && !hasInvalidSelection.value)

const loadCart = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    items.value = await getCart()
  } catch (error) {
    errorMessage.value = error.message || '购物车加载失败'
  } finally {
    loading.value = false
  }
}

const updateItem = async (item, payload) => {
  updatingId.value = item.id
  try {
    const updated = await updateCartItem(item.id, payload)
    const index = items.value.findIndex((entry) => entry.id === item.id)
    if (index >= 0) items.value[index] = updated
  } catch (error) {
    ElMessage.error(error.message || '购物车更新失败')
    await loadCart()
  } finally {
    updatingId.value = undefined
  }
}

const changeQuantity = (item, quantity) => {
  if (quantity === item.quantity) return
  updateItem(item, { quantity })
}

const changeSelected = (item, selected) => {
  updateItem(item, { selected })
}

const toggleAll = async (selected) => {
  const targets = availableItems.value.filter((item) => item.selected !== selected)
  if (!targets.length) return
  loading.value = true
  try {
    await Promise.all(targets.map((item) => updateCartItem(item.id, { selected })))
    await loadCart()
  } catch (error) {
    ElMessage.error(error.message || '全选操作失败')
    await loadCart()
  } finally {
    loading.value = false
  }
}

const removeItem = async (item) => {
  try {
    await ElMessageBox.confirm(
      `确定从购物车删除“${item.productName}”吗？`,
      '删除商品',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '保留' },
    )
    updatingId.value = item.id
    await removeCartItem(item.id)
    items.value = items.value.filter((entry) => entry.id !== item.id)
    ElMessage.success('商品已从购物车移除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '删除失败')
    }
  } finally {
    updatingId.value = undefined
  }
}

const checkout = () => {
  if (hasInvalidSelection.value) {
    ElMessage.warning('请先取消勾选已失效或库存不足的商品')
    return
  }
  if (!selectedItems.value.length) {
    ElMessage.warning('请至少选择一件商品')
    return
  }
  router.push({ name: 'pc-checkout' })
}

onMounted(loadCart)
</script>

<template>
  <div class="cart-page">
    <header class="page-heading">
      <div>
        <p>SHOPPING CART</p>
        <h1>我的购物车</h1>
        <span>调整数量并勾选本次要结算的商品</span>
      </div>
      <el-button @click="router.push({ name: 'pc-products' })">继续购物</el-button>
    </header>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="cart-alert"
    >
      <template #default>
        <el-button link type="primary" @click="loadCart">重新加载</el-button>
      </template>
    </el-alert>

    <section v-loading="loading" class="cart-card">
      <template v-if="items.length">
        <div class="cart-columns">
          <el-checkbox
            :model-value="allSelected"
            :indeterminate="selectedItems.length > 0 && !allSelected"
            @change="toggleAll"
          >
            全选
          </el-checkbox>
          <span>商品信息</span>
          <span>单价</span>
          <span>数量</span>
          <span>小计</span>
          <span>操作</span>
        </div>

        <article
          v-for="item in items"
          :key="item.id"
          class="cart-item"
          :class="{ invalid: !item.available }"
        >
          <el-checkbox
            :model-value="item.selected"
            :disabled="!item.available && !item.selected"
            @change="changeSelected(item, $event)"
          />
          <div class="product-cell">
            <button class="product-image" type="button" @click="router.push(`/pc/products/${item.productId}`)">
              <img v-if="item.productImage" :src="item.productImage" :alt="item.productName" />
              <span v-else>E-Shop</span>
            </button>
            <div>
              <RouterLink :to="`/pc/products/${item.productId}`">{{ item.productName }}</RouterLink>
              <p>{{ specsText(item.specsJson) || '默认规格' }}</p>
              <el-tag v-if="!item.available" type="danger" size="small">
                {{ item.stock < item.quantity ? `库存仅剩 ${item.stock} 件` : '商品已失效' }}
              </el-tag>
            </div>
          </div>
          <strong class="unit-price">{{ formatMoney(item.price) }}</strong>
          <el-input-number
            :model-value="item.quantity"
            :min="1"
            :max="Math.max(Math.min(item.stock, 99), 1)"
            :disabled="updatingId === item.id || !item.available"
            size="small"
            @change="changeQuantity(item, $event)"
          />
          <strong class="subtotal">{{ formatMoney(item.subtotal) }}</strong>
          <el-button
            link
            type="danger"
            :loading="updatingId === item.id"
            @click="removeItem(item)"
          >
            删除
          </el-button>
        </article>

        <el-alert
          v-if="hasInvalidSelection"
          title="已勾选的商品中存在失效或库存不足项，请取消勾选后再结算。"
          type="warning"
          show-icon
          :closable="false"
          class="invalid-notice"
        />

        <footer class="cart-summary">
          <span>已选择 <b>{{ selectedCount }}</b> 件商品</span>
          <div>
            <span>合计（不含运费）</span>
            <strong>{{ formatMoney(totalAmount) }}</strong>
          </div>
          <el-button type="primary" size="large" :disabled="!canCheckout" @click="checkout">
            去结算
          </el-button>
        </footer>
      </template>

      <el-empty v-else-if="!loading" description="购物车还是空的">
        <el-button type="primary" @click="router.push({ name: 'pc-products' })">去挑选商品</el-button>
      </el-empty>
    </section>
  </div>
</template>

<style scoped>
.cart-page { width: min(1220px, 100%); margin: 0 auto; padding: 16px 0 56px; }
.page-heading { display: flex; align-items: end; justify-content: space-between; margin-bottom: 24px; }
.page-heading p { margin: 0 0 6px; color: #2563eb; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.page-heading h1 { margin: 0; color: #0f172a; font-size: 32px; }
.page-heading span { display: block; margin-top: 8px; color: #64748b; }
.cart-alert { margin-bottom: 18px; }
.cart-card { min-height: 330px; overflow: hidden; background: #fff; border: 1px solid #e2e8f0; border-radius: 18px; box-shadow: 0 14px 40px rgba(15, 23, 42, .06); }
.cart-columns, .cart-item { display: grid; grid-template-columns: 74px minmax(340px, 1fr) 110px 145px 120px 70px; gap: 16px; align-items: center; padding: 16px 24px; }
.cart-columns { color: #64748b; background: #f8fafc; border-bottom: 1px solid #e2e8f0; font-size: 13px; }
.cart-item { min-height: 132px; border-bottom: 1px solid #eef2f7; }
.cart-item.invalid { background: #fffafa; }
.product-cell { display: flex; align-items: center; min-width: 0; gap: 16px; }
.product-image { display: grid; flex: 0 0 94px; width: 94px; height: 94px; place-items: center; overflow: hidden; padding: 0; color: #93c5fd; background: #eff6ff; border: 0; border-radius: 12px; cursor: pointer; font-weight: 800; }
.product-image img { width: 100%; height: 100%; object-fit: contain; }
.product-cell a { display: block; overflow: hidden; color: #0f172a; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.product-cell a:hover { color: #2563eb; }
.product-cell p { margin: 8px 0; color: #64748b; font-size: 13px; }
.unit-price { color: #334155; }
.subtotal { color: #dc2626; }
.invalid-notice { margin: 18px 24px 0; width: auto; }
.cart-summary { display: flex; align-items: center; justify-content: flex-end; gap: 28px; min-height: 88px; padding: 18px 24px; background: #f8fafc; }
.cart-summary > span { margin-right: auto; color: #64748b; }
.cart-summary b { color: #2563eb; }
.cart-summary > div { display: flex; align-items: baseline; gap: 12px; color: #64748b; }
.cart-summary strong { color: #dc2626; font-size: 28px; }
.cart-summary .el-button { min-width: 140px; }
@media (max-width: 980px) {
  .cart-columns { display: none; }
  .cart-item { grid-template-columns: 30px 1fr auto; gap: 12px; padding: 18px; }
  .product-cell { grid-column: 2 / 4; }
  .unit-price { grid-column: 2; }
  .subtotal { grid-column: 3; }
  .cart-summary { align-items: stretch; flex-direction: column; gap: 12px; }
  .cart-summary > span { margin-right: 0; }
  .cart-summary > div { justify-content: space-between; }
}
</style>
