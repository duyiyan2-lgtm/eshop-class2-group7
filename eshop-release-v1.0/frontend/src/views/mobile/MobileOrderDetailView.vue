<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { useRoute, useRouter } from 'vue-router'
import { cancelOrder, confirmOrder, getOrder, getOrderLogs } from '../../api/order'
import { uploadUserImage } from '../../api/file'
import { createReview, getReviewedOrderItemIds } from '../../api/review'
import { formatDateTime, formatMoney, orderStatusInfo, specsText } from '../../utils/shop'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const logs = ref([])
const loading = ref(false)
const operating = ref(false)
const errorMessage = ref('')
const logErrorMessage = ref('')
const reviewedItemIds = ref(new Set())
const reviewLookupLoading = ref(false)
const reviewLookupError = ref('')
const showReview = ref(false)
const reviewSubmitting = ref(false)
const reviewItem = ref(null)
const reviewForm = reactive({
  rating: 5,
  content: '',
  imageUrls: [],
})
const reviewUploading = ref(false)
let requestSequence = 0
let reviewRequestSequence = 0

const status = computed(() => orderStatusInfo(order.value?.status))
const totalQuantity = computed(() =>
  (order.value?.items || []).reduce((count, item) => count + item.quantity, 0),
)

const loadReviewStatus = async (items = []) => {
  const requestId = ++reviewRequestSequence
  reviewedItemIds.value = new Set()
  reviewLookupError.value = ''
  reviewLookupLoading.value = false
  if (!items.length) return

  reviewLookupLoading.value = true
  try {
    const ids = await getReviewedOrderItemIds(items.map((item) => item.id))
    if (requestId === reviewRequestSequence) reviewedItemIds.value = ids
  } catch (error) {
    if (requestId === reviewRequestSequence) {
      reviewLookupError.value = error.message || '评价状态加载失败'
    }
  } finally {
    if (requestId === reviewRequestSequence) reviewLookupLoading.value = false
  }
}

const loadDetail = async () => {
  const requestId = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  logErrorMessage.value = ''
  order.value = null
  logs.value = []
  reviewRequestSequence += 1
  reviewedItemIds.value = new Set()
  reviewLookupLoading.value = false
  reviewLookupError.value = ''
  try {
    const id = Number(route.params.id)
    if (!Number.isInteger(id) || id <= 0) throw new Error('订单编号不正确')
    const [orderResult, logResult] = await Promise.allSettled([getOrder(id), getOrderLogs(id)])
    if (requestId !== requestSequence) return
    if (orderResult.status === 'rejected') throw orderResult.reason

    order.value = orderResult.value
    if (orderResult.value.status === 'COMPLETED') {
      void loadReviewStatus(orderResult.value.items || [])
    }
    if (logResult.status === 'fulfilled') {
      logs.value = logResult.value || []
    } else {
      logErrorMessage.value = logResult.reason?.message || '订单进度加载失败'
    }
  } catch (error) {
    if (requestId !== requestSequence) return
    errorMessage.value = error.message || '订单详情加载失败'
  } finally {
    if (requestId === requestSequence) loading.value = false
  }
}

const openReview = (item) => {
  if (order.value?.status !== 'COMPLETED') {
    showToast('订单完成后才能评价商品')
    return
  }
  if (reviewedItemIds.value.has(item.id)) {
    showToast('该商品已经评价')
    return
  }
  reviewItem.value = item
  reviewForm.rating = 5
  reviewForm.content = ''
  reviewForm.imageUrls = []
  showReview.value = true
}

const onReviewImageChange = async (event) => {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (reviewForm.imageUrls.length >= 3) {
    showToast({ type: 'fail', message: '评价图片最多上传 3 张' })
    return
  }
  reviewUploading.value = true
  try {
    const data = await uploadUserImage(file)
    if (data?.url) {
      reviewForm.imageUrls = [...reviewForm.imageUrls, data.url]
    }
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '图片上传失败' })
  } finally {
    reviewUploading.value = false
  }
}

const removeReviewImage = (index) => {
  reviewForm.imageUrls = reviewForm.imageUrls.filter((_, i) => i !== index)
}

const submitReview = async () => {
  if (reviewSubmitting.value || !reviewItem.value) return false
  const content = reviewForm.content.trim()
  const rating = Number(reviewForm.rating)
  if (!Number.isFinite(rating) || rating < 1 || rating > 5) {
    showToast({ type: 'fail', message: '请选择 1—5 星评分' })
    return false
  }
  if (!content || content.length > 1000) {
    showToast({
      type: 'fail',
      message: content ? '评价内容不能超过 1000 个字符' : '请填写评价内容',
    })
    return false
  }
  if (reviewForm.imageUrls.length > 3) {
    showToast({ type: 'fail', message: '评价图片最多上传 3 张' })
    return false
  }

  const orderItemId = reviewItem.value.id
  reviewSubmitting.value = true
  try {
    await createReview({
      orderItemId,
      rating: Math.round(rating),
      content,
      imageUrls: reviewForm.imageUrls,
    })
    reviewedItemIds.value = new Set([...reviewedItemIds.value, orderItemId])
    showToast({ type: 'success', message: '评价发布成功' })
    return true
  } catch (error) {
    const message = error.message || '评价发布失败'
    if (message.includes('已经评价')) {
      reviewedItemIds.value = new Set([...reviewedItemIds.value, orderItemId])
      showToast(message)
      return true
    }
    showToast({ type: 'fail', message })
    return false
  } finally {
    reviewSubmitting.value = false
  }
}

const beforeReviewClose = (action) => {
  if (reviewSubmitting.value) return false
  if (action !== 'confirm') return true
  return submitReview()
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

const goProduct = (item) => {
  const productId = Number(item?.productId)
  if (!Number.isInteger(productId) || productId <= 0) {
    showToast('该商品信息暂时不可用')
    return
  }
  router.push({ name: 'mobile-product-detail', params: { id: productId } })
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
        <van-notice-bar
          v-if="reviewLookupError"
          left-icon="warning-o"
          :text="reviewLookupError"
          color="#b45309"
          background="#fef3c7"
        />
        <van-cell
          v-for="item in (order.items || [])"
          :key="item.id"
          :title="item.productName"
          :label="item.configurationSummary || specsText(item.skuSpecs) || '默认规格'"
          :clickable="Boolean(item.productId)"
          :is-link="Boolean(item.productId)"
          class="item-cell"
          @click="goProduct(item)"
        >
          <template #value>
            <div class="item-value">
              <span>{{ formatMoney(item.price) }} × {{ item.quantity }}</span>
              <strong>{{ formatMoney(item.subtotal) }}</strong>
              <van-button
                v-if="order.status === 'COMPLETED' && reviewedItemIds.has(item.id)"
                size="mini"
                disabled
              >
                已评价
              </van-button>
              <van-button
                v-else-if="order.status === 'COMPLETED'"
                size="mini"
                type="primary"
                plain
                :loading="reviewLookupLoading"
                @click.stop="openReview(item)"
              >
                评价
              </van-button>
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
        <van-notice-bar
          v-if="logErrorMessage"
          left-icon="warning-o"
          :text="logErrorMessage"
          color="#b45309"
          background="#fef3c7"
        />
        <van-steps v-if="logs.length" direction="vertical" :active="logs.length - 1" active-color="#e1251b">
          <van-step v-for="log in logs" :key="log.id">
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
        <van-button
          v-if="order.status === 'COMPLETED'"
          block
          plain
          round
          type="primary"
          @click="router.push({ name: 'mobile-reviews' })"
        >
          我的评价
        </van-button>
        <van-button block round @click="router.push({ name: 'mobile-orders' })">
          返回订单列表
        </van-button>
      </div>
    </template>

    <van-empty v-else-if="!loading" description="订单数据加载失败">
      <van-button round type="primary" size="small" @click="loadDetail">重新加载</van-button>
    </van-empty>
    <van-loading v-if="loading" size="24" class="loading" />

    <van-dialog
      v-model:show="showReview"
      title="发表商品评价"
      show-cancel-button
      :confirm-button-loading="reviewSubmitting"
      :before-close="beforeReviewClose"
      close-on-click-overlay
    >
      <div v-if="reviewItem" class="review-form">
        <div class="review-product">
          <strong>{{ reviewItem.productName }}</strong>
          <span>{{ specsText(reviewItem.skuSpecs) || '默认规格' }}</span>
        </div>
        <div class="rating-row">
          <span>商品评分</span>
          <van-rate v-model="reviewForm.rating" color="#f59e0b" void-icon="star" void-color="#d1d5db" />
        </div>
        <van-field
          v-model="reviewForm.content"
          type="textarea"
          rows="4"
          autosize
          maxlength="1000"
          show-word-limit
          placeholder="请分享商品质量、规格和使用体验"
        />
        <div class="review-images">
          <div v-for="(url, index) in reviewForm.imageUrls" :key="url" class="review-image-item">
            <img :src="url" alt="评价图片" />
            <button type="button" @click="removeReviewImage(index)">删</button>
          </div>
          <label v-if="reviewForm.imageUrls.length < 3" class="upload-tile">
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              hidden
              :disabled="reviewUploading || reviewSubmitting"
              @change="onReviewImageChange"
            />
            <span>{{ reviewUploading ? '…' : '+图' }}</span>
          </label>
        </div>
      </div>
    </van-dialog>
  </section>
</template>

<style scoped>
.mobile-detail { padding-bottom: 24px; background: #f7f8fa; min-height: 100%; }
.error-banner { padding: 12px 16px; color: #b91c1c; background: #fee2e2; font-size: 13px; }
.status-card {
  margin: 12px 12px 16px;
  padding: 24px 22px;
  color: #fff;
  background: linear-gradient(120deg, #0f172a, #7f1d1d);
  border-radius: 14px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, .18);
}
.status-card p { margin: 0 0 6px; color: #f5a09a; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.status-card h1 { margin: 0 0 8px; font-size: 22px; }
.status-card span { color: #cbd5e1; font-size: 12px; }
.status-card.status-PAID { background: linear-gradient(120deg, #7f1d1d, #e1251b); }
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
  color: #f5a09a;
  background: #fff1f0;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 800;
}
.thumb img { width: 100%; height: 100%; object-fit: contain; }
.review-images { display: flex; flex-wrap: wrap; gap: 8px; margin: 0 16px 12px; }
.review-image-item { position: relative; width: 64px; height: 64px; overflow: hidden; border-radius: 8px; background: #f1f5f9; }
.review-image-item img { width: 100%; height: 100%; object-fit: cover; }
.review-image-item button { position: absolute; right: 2px; bottom: 2px; padding: 0 4px; border: 0; border-radius: 4px; color: #fff; background: rgba(15, 23, 42, .75); font-size: 10px; }
.upload-tile { display: grid; width: 64px; height: 64px; place-items: center; border: 1px dashed #cbd5e1; border-radius: 8px; color: #64748b; background: #f8fafc; font-size: 12px; }
.item-value { display: grid; gap: 4px; text-align: right; }
.item-value span { color: #969799; font-size: 12px; }
.item-value strong { color: #dc2626; font-size: 14px; }

.amount-row :deep(.van-cell__value) { color: #dc2626; font-weight: 700; font-size: 16px; }

.action-bar { padding: 16px; display: grid; gap: 10px; background: #fff; }
.action-bar .van-button + .van-button { margin-top: 0; }
.review-form { display: grid; gap: 12px; padding: 12px 16px 6px; }
.review-product { display: grid; gap: 4px; padding: 12px; background: #f7f8fa; border-radius: 10px; }
.review-product strong { color: #323233; }
.review-product span { color: #969799; font-size: 12px; }
.rating-row { display: flex; align-items: center; justify-content: space-between; color: #646566; font-size: 14px; }

:deep(.van-step) h3 { margin: 0 0 4px; color: #323233; font-size: 14px; font-weight: 600; }
:deep(.van-step) p { margin: 2px 0; color: #64748b; font-size: 12px; }
:deep(.van-step) span { color: #94a3b8; font-size: 12px; }
:deep(.van-step) small { display: block; margin-top: 4px; color: #94a3b8; font-size: 11px; }

.loading { display: block; margin: 32px auto; }
</style>
