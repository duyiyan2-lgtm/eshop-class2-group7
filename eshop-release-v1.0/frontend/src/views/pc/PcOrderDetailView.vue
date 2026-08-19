<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  cancelOrder,
  confirmOrder,
  getOrder,
  getOrderLogs,
} from '../../api/order'
import { uploadUserImage } from '../../api/file'
import { createReview, getReviewedOrderItemIds } from '../../api/review'
import {
  formatDateTime,
  formatMoney,
  orderStatusInfo,
  specsText,
} from '../../utils/shop'
import { lineSpecText, productBrowsePath } from '../../utils/vehicle'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const logs = ref([])
const loading = ref(false)
const operating = ref(false)
const errorMessage = ref('')
const logErrorMessage = ref('')
const logLoading = ref(false)
const reviewedItemIds = ref(new Set())
const reviewLookupLoading = ref(false)
const reviewLookupError = ref('')
const reviewDialogVisible = ref(false)
const reviewSubmitting = ref(false)
const reviewItem = ref(null)
const reviewForm = reactive({
  rating: 5,
  content: '',
  imageUrls: [],
})
const reviewUploading = ref(false)
let reviewRequestSequence = 0
let detailRequestSequence = 0
const status = computed(() => orderStatusInfo(order.value?.status))

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
  const requestId = ++detailRequestSequence
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
    if (requestId !== detailRequestSequence) return
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
    if (requestId !== detailRequestSequence) return
    errorMessage.value = error.message || '订单详情加载失败'
  } finally {
    if (requestId === detailRequestSequence) loading.value = false
  }
}

const reloadLogs = async () => {
  const id = Number(route.params.id)
  if (!Number.isInteger(id) || id <= 0 || logLoading.value) return
  logLoading.value = true
  logErrorMessage.value = ''
  try {
    logs.value = await getOrderLogs(id)
  } catch (error) {
    logErrorMessage.value = error.message || '订单进度加载失败'
  } finally {
    logLoading.value = false
  }
}

const cancel = async () => {
  try {
    await ElMessageBox.confirm(
      `确定取消订单 ${order.value.orderNo} 吗？`,
      '取消订单',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '暂不取消' },
    )
    operating.value = true
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    await loadDetail()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '取消订单失败')
    }
  } finally {
    operating.value = false
  }
}

const confirmReceipt = async () => {
  try {
    await ElMessageBox.confirm(
      '请确认已收到订单中的全部商品。',
      '确认收货',
      { type: 'success', confirmButtonText: '确认收货', cancelButtonText: '稍后再说' },
    )
    operating.value = true
    await confirmOrder(order.value.id)
    ElMessage.success('订单已完成')
    await loadDetail()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '确认收货失败')
    }
  } finally {
    operating.value = false
  }
}

const openReview = (item) => {
  if (order.value?.status !== 'COMPLETED') {
    ElMessage.warning('订单完成后才能评价商品')
    return
  }
  if (reviewedItemIds.value.has(item.id)) {
    ElMessage.info('该商品已经评价')
    return
  }
  reviewItem.value = item
  reviewForm.rating = 5
  reviewForm.content = ''
  reviewForm.imageUrls = []
  reviewDialogVisible.value = true
}

const onReviewImageChange = async (event) => {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (reviewForm.imageUrls.length >= 3) {
    ElMessage.warning('评价图片最多上传 3 张')
    return
  }
  reviewUploading.value = true
  try {
    const data = await uploadUserImage(file)
    if (data?.url) {
      reviewForm.imageUrls = [...reviewForm.imageUrls, data.url]
    }
  } catch (error) {
    ElMessage.error(error.message || '图片上传失败')
  } finally {
    reviewUploading.value = false
  }
}

const removeReviewImage = (index) => {
  reviewForm.imageUrls = reviewForm.imageUrls.filter((_, i) => i !== index)
}

const submitReview = async () => {
  if (reviewSubmitting.value || !reviewItem.value) return
  const content = reviewForm.content.trim()
  const rating = Number(reviewForm.rating)
  if (!Number.isFinite(rating) || rating < 1 || rating > 5) {
    ElMessage.warning('请选择 1—5 星评分')
    return
  }
  if (!content || content.length > 1000) {
    ElMessage.warning(content ? '评价内容不能超过 1000 个字符' : '请填写评价内容')
    return
  }
  if (reviewForm.imageUrls.length > 3) {
    ElMessage.warning('评价图片最多上传 3 张')
    return
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
    reviewDialogVisible.value = false
    ElMessage.success('评价发布成功')
  } catch (error) {
    const message = error.message || '评价发布失败'
    if (message.includes('已经评价')) {
      reviewedItemIds.value = new Set([...reviewedItemIds.value, orderItemId])
      reviewDialogVisible.value = false
      ElMessage.info(message)
      return
    }
    ElMessage.error(message)
  } finally {
    reviewSubmitting.value = false
  }
}

const beforeReviewDialogClose = (done) => {
  if (!reviewSubmitting.value) done()
}

watch(() => route.params.id, loadDetail, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="detail-page">
    <el-breadcrumb separator="›" class="breadcrumb">
      <el-breadcrumb-item :to="{ name: 'pc-orders' }">我的订单</el-breadcrumb-item>
      <el-breadcrumb-item>订单详情</el-breadcrumb-item>
    </el-breadcrumb>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
    />

    <template v-if="order">
      <section class="status-card">
        <div>
          <p>ORDER STATUS</p>
          <h1>{{ status.label }}</h1>
          <span>订单号：{{ order.orderNo }}</span>
        </div>
        <div class="status-actions">
          <el-button
            v-if="order.status === 'PENDING_PAYMENT'"
            type="primary"
            size="large"
            @click="router.push({ name: 'pc-order-payment', params: { id: order.id } })"
          >
            立即支付
          </el-button>
          <el-button
            v-if="order.status === 'PENDING_PAYMENT'"
            size="large"
            :loading="operating"
            @click="cancel"
          >
            取消订单
          </el-button>
          <el-button
            v-if="order.status === 'SHIPPED'"
            type="success"
            size="large"
            :loading="operating"
            @click="confirmReceipt"
          >
            确认收货
          </el-button>
          <el-button
            v-if="order.status === 'COMPLETED'"
            plain
            size="large"
            @click="router.push({ name: 'pc-reviews' })"
          >
            我的评价
          </el-button>
        </div>
      </section>

      <div class="detail-grid">
        <main>
          <section class="detail-card">
            <h2>商品信息</h2>
            <el-alert
              v-if="reviewLookupError"
              :title="reviewLookupError"
              type="warning"
              show-icon
              :closable="false"
              class="review-alert"
            />
            <article v-for="item in order.items" :key="item.id" class="order-item">
              <div class="product-image">
                <img v-if="item.productImage" :src="item.productImage" :alt="item.productName" />
                <span v-else>E-Shop</span>
              </div>
              <div class="product-info">
                <RouterLink :to="productBrowsePath(item)">{{ item.productName }}</RouterLink>
                <p>{{ lineSpecText(item, specsText(item.skuSpecs)) }}</p>
              </div>
              <span>{{ formatMoney(item.price) }} × {{ item.quantity }}</span>
              <strong>{{ formatMoney(item.subtotal) }}</strong>
              <div v-if="order.status === 'COMPLETED'" class="review-action">
                <el-button
                  v-if="reviewedItemIds.has(item.id)"
                  size="small"
                  disabled
                >
                  已评价
                </el-button>
                <el-button
                  v-else
                  type="primary"
                  size="small"
                  :loading="reviewLookupLoading"
                  @click="openReview(item)"
                >
                  评价
                </el-button>
              </div>
            </article>
            <footer class="amount-row">
              <span>订单总额</span>
              <strong>{{ formatMoney(order.totalAmount) }}</strong>
            </footer>
          </section>

          <section class="detail-card info-card">
            <h2>配送与备注</h2>
            <dl>
              <dt>收货人</dt><dd>{{ order.receiverName }}</dd>
              <dt>联系电话</dt><dd>{{ order.receiverPhone }}</dd>
              <dt>收货地址</dt><dd>{{ order.receiverAddress }}</dd>
              <dt>订单备注</dt><dd>{{ order.remark || '无' }}</dd>
            </dl>
          </section>
        </main>

        <aside class="detail-card timeline-card">
          <h2>订单进度</h2>
          <div v-if="logErrorMessage" class="timeline-error">
            <el-alert :title="logErrorMessage" type="warning" show-icon :closable="false" />
            <el-button size="small" :loading="logLoading" @click="reloadLogs">重新加载</el-button>
          </div>
          <el-timeline>
            <el-timeline-item
              v-for="log in [...logs].reverse()"
              :key="log.id"
              :timestamp="formatDateTime(log.createdAt)"
              placement="top"
              type="primary"
            >
              <b>{{ orderStatusInfo(log.toStatus).label }}</b>
              <p>{{ log.remark }}</p>
              <span v-if="log.operatorName">操作人：{{ log.operatorName }}</span>
            </el-timeline-item>
          </el-timeline>
          <div class="time-summary">
            <div><span>创建时间</span><b>{{ formatDateTime(order.createdAt) }}</b></div>
            <div v-if="order.paidAt"><span>支付时间</span><b>{{ formatDateTime(order.paidAt) }}</b></div>
            <div v-if="order.shippedAt"><span>发货时间</span><b>{{ formatDateTime(order.shippedAt) }}</b></div>
            <div v-if="order.completedAt"><span>完成时间</span><b>{{ formatDateTime(order.completedAt) }}</b></div>
            <div v-if="order.canceledAt"><span>取消时间</span><b>{{ formatDateTime(order.canceledAt) }}</b></div>
          </div>
        </aside>
      </div>
    </template>

    <el-dialog
      v-model="reviewDialogVisible"
      title="发表商品评价"
      width="min(520px, 92vw)"
      :close-on-click-modal="!reviewSubmitting"
      :close-on-press-escape="!reviewSubmitting"
      :before-close="beforeReviewDialogClose"
    >
      <div v-if="reviewItem" class="review-form">
        <div class="review-product">
          <strong>{{ reviewItem.productName }}</strong>
          <span>{{ specsText(reviewItem.skuSpecs) || '默认规格' }}</span>
        </div>
        <label>商品评分</label>
        <el-rate v-model="reviewForm.rating" size="large" show-text />
        <label for="pc-review-content">评价内容</label>
        <el-input
          id="pc-review-content"
          v-model="reviewForm.content"
          type="textarea"
          :rows="5"
          maxlength="1000"
          show-word-limit
          resize="vertical"
          placeholder="请分享商品质量、规格和使用体验"
        />
        <label>晒图（可选，最多 3 张）</label>
        <div class="review-images">
          <div v-for="(url, index) in reviewForm.imageUrls" :key="url" class="review-image-item">
            <img :src="url" alt="评价图片" />
            <button type="button" @click="removeReviewImage(index)">删除</button>
          </div>
          <label v-if="reviewForm.imageUrls.length < 3" class="upload-tile">
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              hidden
              :disabled="reviewUploading || reviewSubmitting"
              @change="onReviewImageChange"
            />
            <span>{{ reviewUploading ? '上传中…' : '+ 上传' }}</span>
          </label>
        </div>
      </div>
      <template #footer>
        <el-button :disabled="reviewSubmitting" @click="reviewDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="submitReview">
          发布评价
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.detail-page { width: min(1180px, 100%); min-height: 500px; margin: 0 auto; padding: 12px 0 60px; }
.breadcrumb { margin-bottom: 20px; }
.status-card { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; padding: 30px 34px; color: #fff; background: linear-gradient(120deg, #0f172a, #7f1d1d); border-radius: 20px; box-shadow: 0 18px 45px rgba(15, 23, 42, .18); }
.status-card p { margin: 0 0 7px; color: #f5a09a; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.status-card h1 { margin: 0 0 9px; font-size: 30px; }
.status-card span { color: #cbd5e1; font-size: 13px; }
.status-actions { display: flex; gap: 10px; }
.detail-grid { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 18px; }
.detail-card { margin-bottom: 18px; padding: 28px; background: #fff; border: 1px solid #e2e8f0; border-radius: 18px; box-shadow: 0 10px 32px rgba(15, 23, 42, .05); }
.detail-card h2 { margin: 0 0 22px; color: #0f172a; font-size: 20px; }
.order-item { display: grid; grid-template-columns: 72px minmax(0, 1fr) 135px 100px 78px; gap: 15px; align-items: center; padding: 14px 0; border-bottom: 1px solid #eef2f7; }
.product-image { display: grid; width: 68px; height: 68px; place-items: center; overflow: hidden; color: #f5a09a; background: #fff1f0; border-radius: 10px; font-size: 11px; font-weight: 800; }
.product-image img { width: 100%; height: 100%; object-fit: contain; }
.product-info a { color: #0f172a; font-weight: 700; }
.product-info p { margin: 7px 0 0; color: #64748b; font-size: 12px; }
.order-item > span { color: #64748b; }
.order-item > strong { color: #dc2626; text-align: right; }
.review-action { text-align: right; }
.review-alert { margin-bottom: 12px; }
.review-form { display: grid; gap: 13px; }
.review-form label { color: #334155; font-size: 13px; font-weight: 700; }
.review-product { display: grid; gap: 5px; padding: 14px; background: #f8fafc; border-radius: 10px; }
.review-product strong { color: #0f172a; }
.review-product span { color: #64748b; font-size: 12px; }
.review-images { display: flex; flex-wrap: wrap; gap: 10px; }
.review-image-item { position: relative; width: 84px; height: 84px; overflow: hidden; border-radius: 10px; background: #f8fafc; }
.review-image-item img { width: 100%; height: 100%; object-fit: cover; }
.review-image-item button { position: absolute; right: 4px; bottom: 4px; padding: 2px 6px; border: 0; border-radius: 6px; color: #fff; background: rgba(15, 23, 42, .72); font-size: 11px; cursor: pointer; }
.upload-tile { display: grid; width: 84px; height: 84px; place-items: center; border: 1px dashed #cbd5e1; border-radius: 10px; color: #64748b; cursor: pointer; background: #f8fafc; }
.amount-row { display: flex; align-items: baseline; justify-content: flex-end; gap: 18px; padding-top: 22px; color: #64748b; }
.amount-row strong { color: #dc2626; font-size: 28px; }
.info-card dl { display: grid; grid-template-columns: 90px 1fr; gap: 15px; margin: 0; }
.info-card dt { color: #94a3b8; }
.info-card dd { margin: 0; color: #334155; }
.timeline-card { align-self: start; margin-bottom: 0; }
.timeline-error { display: grid; gap: 10px; margin-bottom: 18px; }
.timeline-card :deep(.el-timeline) { padding-left: 6px; }
.timeline-card b { color: #0f172a; }
.timeline-card p { margin: 6px 0; color: #64748b; font-size: 13px; }
.timeline-card span { color: #94a3b8; font-size: 12px; }
.time-summary { padding-top: 18px; border-top: 1px solid #eef2f7; }
.time-summary div { display: grid; gap: 5px; margin: 12px 0; }
.time-summary b { color: #475569; font-size: 13px; }
@media (max-width: 900px) {
  .detail-grid { grid-template-columns: 1fr; }
  .status-card { align-items: start; flex-direction: column; gap: 22px; }
}
@media (max-width: 640px) {
  .order-item { grid-template-columns: 60px 1fr; }
  .order-item > span, .order-item > strong, .review-action { grid-column: 2; text-align: left; }
  .status-actions { align-items: stretch; flex-direction: column; width: 100%; }
}
</style>
