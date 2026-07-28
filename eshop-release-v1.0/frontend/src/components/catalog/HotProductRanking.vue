<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getHotProducts } from '../../api/catalog'
import { formatMoney } from '../../utils/shop'

const props = defineProps({
  mobile: {
    type: Boolean,
    default: false,
  },
  days: {
    type: Number,
    default: 30,
  },
  limit: {
    type: Number,
    default: 5,
  },
})

const router = useRouter()
const loading = ref(false)
const products = ref([])
const errorMessage = ref('')

const load = async () => {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    products.value = (await getHotProducts({ days: props.days, limit: props.limit }))
      .map((item) => ({ ...item, imageFailed: false }))
  } catch (error) {
    products.value = []
    errorMessage.value = error.message || '热销榜加载失败'
  } finally {
    loading.value = false
  }
}

const openProduct = (id) => {
  router.push({
    name: props.mobile ? 'mobile-product-detail' : 'pc-product-detail',
    params: { id },
  })
}

onMounted(load)
</script>

<template>
  <section v-if="loading || errorMessage || products.length" class="hot-ranking" :class="{ mobile }">
    <header>
      <div>
        <span>TOP SELLING</span>
        <h2>近 {{ days }} 天热销榜</h2>
      </div>
      <button v-if="errorMessage" type="button" @click="load">重新加载</button>
      <small v-else>按有效订单销量排序</small>
    </header>

    <div v-if="loading && !products.length" class="loading-list">
      <span v-for="index in Math.min(limit, 5)" :key="index" />
    </div>

    <div v-else-if="products.length" class="ranking-list">
      <button
        v-for="(product, index) in products"
        :key="product.productId"
        type="button"
        class="ranking-card"
        @click="openProduct(product.productId)"
      >
        <b :class="{ champion: index === 0 }">{{ index + 1 }}</b>
        <span class="product-image">
          <img
            v-if="product.mainImage && !product.imageFailed"
            :src="product.mainImage"
            :alt="product.name"
            loading="lazy"
            @error="product.imageFailed = true"
          />
          <span v-else>E-Shop</span>
        </span>
        <span class="product-info">
          <strong>{{ product.name }}</strong>
          <small>已售 {{ product.salesQuantity }} 件</small>
          <em>{{ formatMoney(product.minPrice) }}</em>
        </span>
      </button>
    </div>

    <p v-else class="load-error">{{ errorMessage }}</p>
  </section>
</template>

<style scoped>
.hot-ranking {
  margin: 24px 0;
  padding: 22px;
  background: linear-gradient(135deg, #fff7ed, #fff 55%);
  border: 1px solid #fed7aa;
  border-radius: 18px;
  box-shadow: 0 10px 26px rgba(154, 52, 18, .06);
}

.hot-ranking header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.hot-ranking header span {
  color: #ea580c;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .12em;
}

.hot-ranking h2 {
  margin: 3px 0 0;
  color: #431407;
  font-size: 21px;
}

.hot-ranking header small {
  color: #9a3412;
}

.hot-ranking header button {
  padding: 5px 9px;
  color: #c2410c;
  background: #fff;
  border: 1px solid #fdba74;
  border-radius: 8px;
}

.ranking-list,
.loading-list {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.ranking-card {
  position: relative;
  display: flex;
  min-width: 0;
  gap: 10px;
  padding: 10px;
  text-align: left;
  background: rgba(255, 255, 255, .92);
  border: 1px solid #ffedd5;
  border-radius: 12px;
  cursor: pointer;
  transition: .2s ease;
}

.ranking-card:hover {
  border-color: #fb923c;
  box-shadow: 0 8px 18px rgba(234, 88, 12, .1);
  transform: translateY(-2px);
}

.ranking-card > b {
  position: absolute;
  z-index: 1;
  top: 4px;
  left: 4px;
  display: grid;
  width: 23px;
  height: 23px;
  color: #fff;
  background: #fb923c;
  border-radius: 50%;
  font-size: 12px;
  place-items: center;
}

.ranking-card > b.champion {
  background: #dc2626;
}

.product-image {
  display: grid;
  flex: 0 0 60px;
  width: 60px;
  height: 66px;
  place-items: center;
  overflow: hidden;
  color: #fdba74;
  background: #fff7ed;
  border-radius: 8px;
  font-size: 9px;
  font-weight: 800;
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-info {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
}

.product-info strong {
  overflow: hidden;
  color: #431407;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-info small {
  margin: 4px 0;
  color: #9a3412;
  font-size: 11px;
}

.product-info em {
  color: #dc2626;
  font-size: 13px;
  font-style: normal;
  font-weight: 800;
}

.loading-list span {
  height: 86px;
  background: linear-gradient(100deg, #fff7ed 30%, #ffedd5 50%, #fff7ed 70%);
  background-size: 300% 100%;
  border-radius: 12px;
  animation: pulse 1.2s infinite;
}

.load-error {
  margin: 0;
  color: #c2410c;
  font-size: 13px;
}

@keyframes pulse {
  to { background-position: -100% 0; }
}

@media (max-width: 1000px) {
  .ranking-list,
  .loading-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.hot-ranking.mobile {
  margin: 8px 12px;
  padding: 14px;
  border-radius: 14px;
}

.hot-ranking.mobile header {
  align-items: center;
  margin-bottom: 12px;
}

.hot-ranking.mobile h2 {
  font-size: 16px;
}

.hot-ranking.mobile header small {
  font-size: 10px;
}

.hot-ranking.mobile .ranking-list,
.hot-ranking.mobile .loading-list {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  scrollbar-width: none;
}

.hot-ranking.mobile .ranking-list::-webkit-scrollbar,
.hot-ranking.mobile .loading-list::-webkit-scrollbar {
  display: none;
}

.hot-ranking.mobile .ranking-card,
.hot-ranking.mobile .loading-list span {
  flex: 0 0 180px;
}
</style>
