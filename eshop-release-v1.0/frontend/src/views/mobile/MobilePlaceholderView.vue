<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const description = computed(() => route.meta.placeholderDescription || '业务页面正在开发中')
const owner = computed(() => route.meta.moduleOwner || '对应模块负责人')
</script>

<template>
  <section class="mobile-placeholder">
    <van-empty image="search" :description="description" />
    <van-cell-group inset>
      <van-cell title="页面入口" :value="route.path" />
      <van-cell title="负责模块" :value="owner" />
      <van-cell title="接口约定" value="复用后端 API v1.0" />
    </van-cell-group>
    <p>公共导航、登录保护和路由已经接入；模块负责人只需用正式业务页面替换当前组件。</p>
  </section>
</template>

<style scoped>
.mobile-placeholder { padding: 12px 0 20px; }
.mobile-placeholder :deep(.van-empty) { padding-top: 32px; }
.mobile-placeholder p {
  margin: 18px 28px 0;
  color: #969799;
  font-size: 13px;
  line-height: 1.7;
  text-align: center;
}
</style>
