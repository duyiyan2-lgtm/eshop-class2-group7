<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCategories, getProducts } from '../../api/catalog'
import VehicleDisclaimer from '../../components/vehicle/VehicleDisclaimer.vue'
import { isVehicleProduct, vehicleCoverSrc } from '../../utils/vehicle'
import { formatMoney } from '../../utils/shop'

const router = useRouter()
const route = useRoute()
const configuratorName = computed(() => (
  route.path.startsWith('/m') ? 'mobile-vehicle-configurator' : 'pc-vehicle-configurator'
))
const loading = ref(false)
const errorMessage = ref('')
const category = ref(null)
const records = ref([])

const vehicles = computed(() => records.value.filter(isVehicleProduct))

const load = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const categories = await getCategories()
    const found = (categories || []).find((item) => item.name === '小米汽车（课程演示）')
    category.value = found || null
    if (!found) {
      records.value = []
      errorMessage.value = '尚未找到汽车演示分类，请确认已执行数据库迁移 V13。'
      return
    }
    const page = await getProducts({ current: 1, size: 12, categoryId: found.id })
    records.value = (page.records || []).map((item) => ({ ...item, imageFailed: false }))
  } catch (error) {
    records.value = []
    errorMessage.value = error.message || '车型列表加载失败'
  } finally {
    loading.value = false
  }
}

const openConfigurator = (product) => {
  router.push({ name: configuratorName.value, params: { id: product.id } })
}

onMounted(load)
</script>

<template>
  <div class="vehicle-list">
    <header class="list-hero">
      <div>
        <span>COURSE VEHICLES</span>
        <h1>课程演示车型</h1>
        <p>选择车型后进入选配器。价格为后端计算的课程演示价，不代表官方售价或购车权益。</p>
      </div>
      <VehicleDisclaimer />
    </header>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
    >
      <template #default>
        <el-button link type="primary" @click="load">重新加载</el-button>
      </template>
    </el-alert>

    <section v-loading="loading" class="vehicle-grid">
      <article
        v-for="product in vehicles"
        :key="product.id"
        class="vehicle-card"
      >
        <div class="card-image">
          <img
            v-if="product.mainImage && !product.imageFailed"
            :src="vehicleCoverSrc(product)"
            :alt="product.name"
            width="640"
            height="360"
            loading="lazy"
            decoding="async"
            @error="product.imageFailed = true"
          />
          <img
            v-else
            src="/product-images/vehicles/placeholder.svg"
            alt="E-Shop 汽车示意占位图"
            width="640"
            height="360"
          />
        </div>
        <div class="card-body">
          <h2>{{ product.name }}</h2>
          <p>{{ product.subtitle || '课程演示车型' }}</p>
          <strong>{{ formatMoney(product.minPrice) }} <small>起</small></strong>
          <button type="button" @click="openConfigurator(product)">开始选配</button>
        </div>
      </article>
    </section>

    <el-empty
      v-if="!loading && !errorMessage && vehicles.length === 0"
      description="当前没有可展示的课程演示车型"
    >
      <el-button type="primary" @click="router.push({ name: route.path.startsWith('/m') ? 'mobile-products' : 'pc-products' })">返回商城首页</el-button>
    </el-empty>
  </div>
</template>

<style scoped>
.vehicle-list {
  width: min(1226px, calc(100% - 32px));
  margin: 0 auto;
  padding: 24px 0 56px;
}

.list-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 360px);
  gap: 24px;
  align-items: end;
  margin-bottom: 28px;
}

.list-hero span {
  color: #ff6700;
  font-size: 12px;
  letter-spacing: .16em;
}

.list-hero h1 {
  margin: 6px 0 8px;
  font-size: 32px;
  font-weight: 600;
}

.list-hero p {
  margin: 0;
  color: #6b7380;
  line-height: 1.6;
}

.vehicle-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  min-height: 220px;
}

.vehicle-card {
  overflow: hidden;
  background: #fff;
  border: 1px solid #eceff3;
  border-radius: 16px;
}

.card-image {
  aspect-ratio: 16 / 9;
  background: #eef1f4;
}

.card-image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-body {
  display: grid;
  gap: 8px;
  padding: 18px 20px 20px;
}

.card-body h2 {
  margin: 0;
  font-size: 18px;
}

.card-body p {
  margin: 0;
  color: #7a8088;
  font-size: 13px;
}

.card-body strong {
  color: #ff6700;
  font-size: 22px;
}

.card-body small {
  font-size: 13px;
  font-weight: 500;
}

.card-body button {
  justify-self: start;
  padding: 8px 16px;
  color: #fff;
  background: #333;
  border: 0;
  border-radius: 999px;
  cursor: pointer;
}

.card-body button:hover { background: #ff6700; }

@media (max-width: 1100px) {
  .vehicle-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 768px) {
  .list-hero,
  .vehicle-grid { grid-template-columns: 1fr; }
}
</style>
