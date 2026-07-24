<template>
  <div class="mobile-layout">
    <router-view />
  </div>
</template>

<script setup>
// 移动端布局壳子，限制最大宽度让 PC 浏览器也像手机一样显示
</script>

<style scoped>
.mobile-layout {
  max-width: 750px;
  margin: 0 auto;
  background: #f5f5f5;
  min-height: 100vh;
}
</style>