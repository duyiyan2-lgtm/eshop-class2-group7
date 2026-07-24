<script setup>
import { computed, ref, watch } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { useRoute, useRouter } from 'vue-router'
import { cancelOrder, confirmOrder, getOrder, getOrderLogs } from '../../api/order'
import { formatDateTime, formatMoney, orderStatusInfo, specsText } from '../../utils/shop'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const logs = ref([])
const loading = ref(false)
const operating = ref(false)
const errorMessage = ref('')

const status = computed(() => orderStatusInfo(order.value?.status))
const totalQuantity = computed(() =>
  (order.value?.items || []).reduce((count, item) => count + item.quantity, 0),
)

const loadDetail = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const id = Number(route.params.id)
    if (!Number.isInteger(id) || id <= 0) throw new Error('订单编号不正确')
    const [orderData, logData] = await Promise.all([getOrder(id), getOrderLogs(id)])
    order.value = orderData
    logs.value = logData
  } catch (error) {
    errorMessage.value = error.message || '订单详情加载失败'
  } finally {
    loading.value = false
  }
}

const cancel = async () => {
  try {
    await showConfirmDialog({
      title: '取消订单',
      message: `确定取消订单 ${order.value.orderNo} 吗？取消后库存会自动恢复。`,
      confirmButtonText: '确认取消',
      cancelButtonText: '暂不取消',
    })
    operating.value = true
    await cancelOrder(order.value.id)
    showToast('订单已取消')
    await loadDetail()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    showToast({ type: 'fail', message: error.message || '取消订单失败' })
  } finally {
    operating.value = false
  }
}

const confirmReceipt = async () => {
  try {
    await showConfirmDialog({
      title: '确认收货',
      message: '请确认已收到订单中的全部商品。',
      confirmButtonText: '确认收货',
      cancelButtonText: '稍后再说',
    })
    operating.value = true
    await confirmOrder(order.value.id)
    showToast('订单已完成')
    await loadDetail()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    showToast({ type: 'fail', message: error.message || '确认收货失败' })
  } finally {
    operating.value = false
  }
}

const goPay = () => {
  router.push({ name: 'mobile-order-payment', params: { id: order.value.id } })
}

watch(() => route.params.id, loadDetail, { immediate: true })
</script>

<template>
  <section class="mobile-detail">
    <div v-if="errorMessage" class="error-banner">{{ errorMessage }}</div>

    <template v-if="order">
      <div class="status-card" :class="`status-${order.status}`">
        <p>{{ status.label }}</p>
        <h1>
          <template v-if="order.status === 'PENDING_PAYMENT'">请在 30 分钟内完成支付</template>
          <template v-else-if="order.status === 'PAID'">商家正在备货，请耐心等待</template>
          <template v-else-if="order.status === 'SHIPPED'">商品已发出，请留意物流</template>
          <template v-else-if="order.status === 'COMPLETED'">订单已完成，感谢您的购买</template>
          <template v-else-if="order.status === 'CANCELED'">订单已取消</template>
          <template v-else>订单状态：{{ status.label }}</template>
        </h1>
        <span>订单号：{{ order.orderNo }}</span>
      </div>

      <van-cell-group inset title="配送信息" class="block">
        <van-cell title="收货人" :value="order.receiverName" />
        <van-cell title="联系电话" :value="order.receiverPhone" />
        <van-cell title="收货地址" :value="order.receiverAddress" />
        <van-cell v-if="order.remark" title="订单备注" :value="order.remark" />
      </van-cell-group>

      <van-cell-group inset title="商品列表" class="block">
        <van-cell
          v-for="item in order.items"
          :key="item.id"
          :title="item.productName"
          :label="specsText(item.skuSpecs) || '默认规格'"
          class="item-cell"
        >
          <template #value>
            <div class="item-value">
              <span>{{ formatMoney(item.price) }} × {{ item.quantity }}</span>
              <strong>{{ formatMoney(item.subtotal) }}</strong>
            </div>
          </template>
          <template #icon>
            <div class="thumb">
              <img v-if="item.productImage" :src="item.productImage" :alt="item.productName" />
              <span v-else>E-Shop</span>
            </div>
          </template>
        </van-cell>
      </van-cell-group>

      <van-cell-group inset title="费用明细" class="block">
        <van-cell title="商品总额" :value="formatMoney(order.totalAmount)" />
        <van-cell title="商品件数" :value="`共 ${totalQuantity} 件`" />
        <van-cell title="实付金额" :value="formatMoney(order.totalAmount)" class="amount-row" />
      </van-cell-group>

      <van-cell-group inset title="订单进度" class="block">
        <van-steps v-if="logs.length" direction="vertical" :active="logs.length - 1" active-color="#1d4ed8">
          <van-step v-for="(log, index) in [...logs].reverse()" :key="log.id">
            <h3>{{ orderStatusInfo(log.toStatus).label }}</h3>
            <p>{{ log.remark }}</p>
            <span v-if="log.operatorName">操作人：{{ log.operatorName }}</span>
            <small>{{ formatDateTime(log.createdAt) }}</small>
          </van-step>
        </van-steps>
        <van-empty v-else description="暂无进度记录" image-size="60" />

        <van-cell title="创建时间" :value="formatDateTime(order.createdAt)" />
        <van-cell v-if="order.paidAt" title="支付时间" :value="formatDateTime(order.paidAt)" />
        <van-cell v-if="order.shippedAt" title="发货时间" :value="formatDateTime(order.shippedAt)" />
        <van-cell v-if="order.completedAt" title="完成时间" :value="formatDateTime(order.completedAt)" />
        <van-cell v-if="order.canceledAt" title="取消时间" :value="formatDateTime(order.canceledAt)" />
      </van-cell-group>

      <div class="action-bar">
        <van-button
          v-if="order.status === 'PENDING_PAYMENT'"
          type="primary"
          block
          round
          :loading="operating"
          @click="goPay"
        >
          立即支付
        </van-button>
        <van-button
          v-if="order.status === 'PENDING_PAYMENT'"
          block
          plain
          round
          :loading="operating"
          @click="cancel"
        >
          取消订单
        </van-button>
        <van-button
          v-if="order.status === 'SHIPPED'"
          type="success"
          block
          round
          :loading="operating"
          @click="confirmReceipt"
        >
          确认收货
        </van-button>
        <van-button block round @click="router.push({ name: 'mobile-orders' })">
          返回订单列表
        </van-button>
      </div>
    </template>

    <van-empty v-else-if="!loading" description="订单数据加载失败" />
    <van-loading v-if="loading" size="24" class="loading" />
  </section>
</template>

<style scoped>
.mobile-detail { padding-bottom: 24px; background: #f7f8fa; min-height: 100%; }
.error-banner { padding: 12px 16px; color: #b91c1c; background: #fee2e2; font-size: 13px; }
.status-card {
  margin: 12px 12px 16px;
  padding: 24px 22px;
  color: #fff;
  background: linear-gradient(120deg, #0f172a, #1e3a8a);
  border-radius: 14px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, .18);
}
.status-card p { margin: 0 0 6px; color: #93c5fd; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.status-card h1 { margin: 0 0 8px; font-size: 22px; }
.status-card span { color: #cbd5e1; font-size: 12px; }
.status-card.status-PAID { background: linear-gradient(120deg, #1e3a8a, #2563eb); }
.status-card.status-SHIPPED { background: linear-gradient(120deg, #075985, #0891b2); }
.status-card.status-COMPLETED { background: linear-gradient(120deg, #14532d, #16a34a); }
.status-card.status-CANCELED { background: linear-gradient(120deg, #525252, #737373); }

.block { margin: 0 0 12px; }
.block :deep(.van-cell-group__title) { padding-left: 16px; color: #323233; font-weight: 600; }

.item-cell { padding: 12px 16px; }
.item-cell :deep(.van-cell__icon) { margin-right: 12px; }
.thumb {
  display: grid;
  width: 56px;
  height: 56px;
  place-items: center;
  overflow: hidden;
  color: #93c5fd;
  background: #eff6ff;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 800;
}
.thumb img { width: 100%; height: 100%; object-fit: contain; }
.item-value { display: grid; gap: 4px; text-align: right; }
.item-value span { color: #969799; font-size: 12px; }
.item-value strong { color: #dc2626; font-size: 14px; }

.amount-row :deep(.van-cell__value) { color: #dc2626; font-weight: 700; font-size: 16px; }

.action-bar { padding: 16px; display: grid; gap: 10px; background: #fff; }
.action-bar .van-button + .van-button { margin-top: 0; }

:deep(.van-step) h3 { margin: 0 0 4px; color: #323233; font-size: 14px; font-weight: 600; }
:deep(.van-step) p { margin: 2px 0; color: #64748b; font-size: 12px; }
:deep(.van-step) span { color: #94a3b8; font-size: 12px; }
:deep(.van-step) small { display: block; margin-top: 4px; color: #94a3b8; font-size: 11px; }

.loading { display: block; margin: 32px auto; }
</style>