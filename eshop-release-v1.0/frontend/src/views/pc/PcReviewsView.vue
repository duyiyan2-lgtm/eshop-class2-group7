<script setup>
import ReviewHistoryList from '../../components/review/ReviewHistoryList.vue'
</script>

<template>
  <div class="reviews-page">
    <el-breadcrumb separator="›" class="breadcrumb">
      <el-breadcrumb-item :to="{ name: 'pc-products' }">商品商城</el-breadcrumb-item>
      <el-breadcrumb-item>我的评价</el-breadcrumb-item>
    </el-breadcrumb>
    <header>
      <div>
        <p>MY REVIEWS</p>
        <h1>我的评价</h1>
        <span>查看你对已完成订单商品发表的全部评价</span>
      </div>
      <el-button type="primary" @click="$router.push({ name: 'pc-orders' })">
        查看已完成订单
      </el-button>
    </header>
    <ReviewHistoryList platform="pc" />
  </div>
</template>

<style scoped>
.reviews-page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 12px 0 60px;
}

.breadcrumb {
  margin-bottom: 18px;
}

header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}

header p {
  margin: 0 0 6px;
  color: #e1251b;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .12em;
}

header h1 {
  margin: 0 0 7px;
  color: #0f172a;
  font-size: 32px;
}

header span {
  color: #64748b;
}

@media (max-width: 640px) {
  header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
