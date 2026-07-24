import { createRouter, createWebHistory } from 'vue-router'
import MobileLayout from '@/layouts/MobileLayout.vue'
import OrderList from '@/views/mobile/OrderList.vue'
import OrderDetail from '@/views/mobile/OrderDetail.vue'

const routes = [
  {
    path: '/m',
    component: MobileLayout,
    children: [
      { path: '', redirect: '/m/orders' },
      { path: 'orders', component: OrderList },
      { path: 'orders/:id', component: OrderDetail }
    ]
  }
]

export default createRouter({
  history: createWebHistory(),
  routes
})