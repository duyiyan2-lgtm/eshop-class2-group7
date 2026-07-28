<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminReviews, updateAdminReviewStatus } from '../../api/admin'
import { formatDateTime, specsText } from '../../utils/shop'

const loading = ref(false)
const operatingId = ref(null)
const reviews = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(20)
const keyword = ref('')
const rating = ref()
const status = ref('')
const errorMessage = ref('')
let requestSequence = 0

const loadReviews = async () => {
  const sequence = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await getAdminReviews({
      current: current.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      rating: rating.value || undefined,
      status: status.value || undefined,
    })
    if (sequence !== requestSequence) return
    reviews.value = page.records || []
    total.value = Number(page.total) || 0
  } catch (error) {
    if (sequence !== requestSequence) return
    reviews.value = []
    total.value = 0
    errorMessage.value = error.message || '评价列表加载失败'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

const search = () => {
  current.value = 1
  loadReviews()
}

const reset = () => {
  keyword.value = ''
  rating.value = undefined
  status.value = ''
  search()
}

const changeStatus = async (review) => {
  const nextStatus = review.status === 'PUBLISHED' ? 'HIDDEN' : 'PUBLISHED'
  const action = nextStatus === 'HIDDEN' ? '隐藏' : '恢复'
  try {
    await ElMessageBox.confirm(
      `确定${action}这条评价吗？${nextStatus === 'HIDDEN' ? '隐藏后消费者将无法看到该评价，商品评分也会同步更新。' : ''}`,
      `${action}评价`,
      {
        type: nextStatus === 'HIDDEN' ? 'warning' : 'info',
        confirmButtonText: action,
        cancelButtonText: '取消',
      },
    )
    operatingId.value = review.id
    await updateAdminReviewStatus(review.id, nextStatus)
    ElMessage.success(`${action}成功`)
    await loadReviews()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || `${action}失败`)
    }
  } finally {
    operatingId.value = null
  }
}

onMounted(loadReviews)
</script>

<template>
  <section class="admin-reviews">
    <header class="page-heading">
      <div>
        <p>CONTENT MODERATION</p>
        <h1>评价管理</h1>
        <span>查询消费者评价，对不适合公开展示的内容进行隐藏或恢复。</span>
      </div>
      <el-button :loading="loading" @click="loadReviews">刷新</el-button>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="关键词">
          <el-input
            v-model="keyword"
            class="keyword-input"
            placeholder="商品、用户或评价内容"
            clearable
            @clear="search"
          />
        </el-form-item>
        <el-form-item label="评分">
          <el-select v-model="rating" class="filter-select" clearable placeholder="全部" @change="search">
            <el-option v-for="score in [5, 4, 3, 2, 1]" :key="score" :label="`${score} 星`" :value="score" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="status" class="filter-select" clearable placeholder="全部" @change="search">
            <el-option label="公开展示" value="PUBLISHED" />
            <el-option label="已隐藏" value="HIDDEN" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading">搜索</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="error-alert"
    >
      <template #default>
        <el-button link type="primary" @click="loadReviews">重新加载</el-button>
      </template>
    </el-alert>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="reviews" stripe empty-text="暂无符合条件的评价">
        <el-table-column label="商品" min-width="190">
          <template #default="{ row }">
            <strong class="product-name">{{ row.productName }}</strong>
            <small>{{ specsText(row.skuSpecs) || '默认规格' }}</small>
          </template>
        </el-table-column>
        <el-table-column label="评价用户" min-width="140">
          <template #default="{ row }">
            <span>{{ row.reviewerNickname }}</span>
            <small>{{ row.username }}</small>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="120">
          <template #default="{ row }">
            <el-rate :model-value="row.rating" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="评价内容" min-width="280">
          <template #default="{ row }">
            <p class="review-content">{{ row.content }}</p>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
              {{ row.status === 'PUBLISHED' ? '公开' : '已隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评价时间" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="105" fixed="right">
          <template #default="{ row }">
            <el-button
              :type="row.status === 'PUBLISHED' ? 'danger' : 'success'"
              size="small"
              plain
              :loading="operatingId === row.id"
              @click="changeStatus(row)"
            >
              {{ row.status === 'PUBLISHED' ? '隐藏' : '恢复' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="current"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadReviews"
          @size-change="search"
        />
      </div>
    </el-card>
  </section>
</template>

<style scoped>
.admin-reviews {
  max-width: 1240px;
  margin: 0 auto;
}

.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.page-heading p {
  margin: 0 0 5px;
  color: #409eff;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .12em;
}

.page-heading h1 {
  margin: 0 0 6px;
  color: #303133;
  font-size: 25px;
}

.page-heading span,
td small {
  display: block;
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.filter-card,
.error-alert {
  margin-bottom: 16px;
}

.keyword-input {
  width: 260px;
}

.filter-select {
  width: 130px;
}

.product-name {
  color: #303133;
}

.review-content {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
