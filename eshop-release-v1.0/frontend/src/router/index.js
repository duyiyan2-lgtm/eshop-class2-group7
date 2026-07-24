import { createRouter, createWebHistory } from 'vue-router'
import MobileOrdersView from '@/views/mobile/MobileOrdersView.vue'
import MobileOrderDetailView from '@/views/mobile/MobileOrderDetailView.vue'
import MobileOrderPaymentView from '@/views/mobile/MobileOrderPaymentView.vue'

const routes = [
  // C 模块（订单支付）：手机端 3 个核心页面
  {
    path: '/',
    redirect: '/m/orders',
  },
  {
    path: '/m/orders',
    name: 'mobile-orders',
    component: MobileOrdersView,
    meta: { title: '我的订单', requiresAuth: false },
  },
  {
    path: '/m/orders/:id/pay',
    name: 'mobile-order-payment',
    component: MobileOrderPaymentView,
    meta: { title: '模拟支付', requiresAuth: false },
  },
  {
    path: '/m/orders/:id',
    name: 'mobile-order-detail',
    component: MobileOrderDetailView,
    meta: { title: '订单详情', requiresAuth: false },
  },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})