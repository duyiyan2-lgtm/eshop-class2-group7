<script setup>
import ReviewHistoryList from '../../components/review/ReviewHistoryList.vue'
</script>

<template>
  <section class="mobile-reviews">
    <div class="review-hero">
      <p>MY REVIEWS</p>
      <h1>我的评价</h1>
      <span>已发表的商品评价会展示在对应商品详情中</span>
    </div>
    <ReviewHistoryList platform="mobile" :page-size="8" />
  </section>
</template>

<style scoped>
.mobile-reviews {
  box-sizing: border-box;
  min-height: 100%;
  padding: 12px 12px 28px;
  background: #f7f8fa;
}

.review-hero {
  margin-bottom: 12px;
  padding: 22px 20px;
  color: #fff;
  background: linear-gradient(135deg, #7f1d1d, #ff6a00);
  border-radius: 14px;
  box-shadow: 0 12px 28px rgba(225, 37, 27, .18);
}

.review-hero p {
  margin: 0 0 6px;
  color: #ffd7c2;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .14em;
}

.review-hero h1 {
  margin: 0 0 7px;
  font-size: 24px;
}

.review-hero span {
  color: #ffe8d6;
  font-size: 13px;
}
</style>
