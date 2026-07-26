import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '../pinia'
import { useAuthStore } from '../stores/auth'

const MobilePlaceholderView = () => import('../views/mobile/MobilePlaceholderView.vue')

const routes = [
  { path: '/', redirect: '/pc' },
  {
    path: '/pc/login',
    name: 'pc-login',
    component: () => import('../views/pc/PcLoginView.vue'),
  },
  {
    path: '/pc',
    component: () => import('../layouts/PcShopLayout.vue'),
    children: [
      { path: '', redirect: { name: 'pc-products' } },
      {
        path: 'products',
        name: 'pc-products',
        component: () => import('../views/pc/PcProductListView.vue'),
      },
      {
        path: 'products/:id',
        name: 'pc-product-detail',
        component: () => import('../views/pc/PcProductDetailView.vue'),
      },
      {
        path: 'cart',
        name: 'pc-cart',
        component: () => import('../views/pc/PcCartView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'addresses',
        name: 'pc-addresses',
        component: () => import('../views/pc/PcAddressView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'checkout',
        name: 'pc-checkout',
        component: () => import('../views/pc/PcCheckoutView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'orders',
        name: 'pc-orders',
        component: () => import('../views/pc/PcOrdersView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'orders/:id',
        name: 'pc-order-detail',
        component: () => import('../views/pc/PcOrderDetailView.vue'),
        meta: { requiresAuth: true },
      },
      {
        path: 'orders/:id/pay',
        name: 'pc-order-payment',
        component: () => import('../views/pc/PcPaymentView.vue'),
        meta: { requiresAuth: true },
      },
    ],
  },
  {
    path: '/m/login',
    name: 'mobile-login',
    component: () => import('../views/mobile/MobileLoginView.vue'),
  },
  {
    path: '/m',
    component: () => import('../layouts/MobileShopLayout.vue'),
    children: [
      { path: '', redirect: { name: 'mobile-products' } },
      {
        path: 'products',
        name: 'mobile-products',
        component: () => import('../views/mobile/MobileHomeView.vue'),
        meta: { title: '手机商城', mobileTabbar: true },
      },
      {
        path: 'products/:id',
        name: 'mobile-product-detail',
        component: MobilePlaceholderView,
        meta: {
          title: '商品详情',
          moduleOwner: '商品模块',
          placeholderDescription: '手机商品详情页面待接入',
        },
      },
      {
        path: 'cart',
        name: 'mobile-cart',
        component: MobilePlaceholderView,
        meta: {
          title: '购物车',
          mobileTabbar: true,
          requiresAuth: true,
          moduleOwner: '用户与购物车模块',
          placeholderDescription: '手机购物车页面待接入',
        },
      },
      {
        path: 'addresses',
        name: 'mobile-addresses',
        component: MobilePlaceholderView,
        meta: {
          title: '收货地址',
          requiresAuth: true,
          moduleOwner: '用户与购物车模块',
          placeholderDescription: '手机收货地址页面待接入',
        },
      },
      {
        path: 'checkout',
        name: 'mobile-checkout',
        component: MobilePlaceholderView,
        meta: {
          title: '确认订单',
          requiresAuth: true,
          moduleOwner: '订单与支付模块',
          placeholderDescription: '手机订单确认页面待接入',
        },
      },
      {
        path: 'orders',
        name: 'mobile-orders',
        component: () => import('../views/mobile/MobileOrdersView.vue'),
        meta: {
          title: '我的订单',
          mobileTabbar: true,
          requiresAuth: true,
          moduleOwner: '订单与支付模块',
        },
      },
      {
        path: 'orders/:id/pay',
        name: 'mobile-order-payment',
        component: () => import('../views/mobile/MobileOrderPaymentView.vue'),
        meta: {
          title: '模拟支付',
          requiresAuth: true,
          moduleOwner: '订单与支付模块',
        },
      },
      {
        path: 'orders/:id',
        name: 'mobile-order-detail',
        component: () => import('../views/mobile/MobileOrderDetailView.vue'),
        meta: {
          title: '订单详情',
          requiresAuth: true,
          moduleOwner: '订单与支付模块',
        },
      },
      {
        path: 'profile',
        name: 'mobile-profile',
        component: MobilePlaceholderView,
        meta: {
          title: '个人中心',
          mobileTabbar: true,
          requiresAuth: true,
          moduleOwner: '用户与购物车模块',
          placeholderDescription: '手机个人中心页面待接入',
        },
      },
    ],
  },
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('../views/admin/AdminLoginView.vue'),
  },
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', name: 'admin-home', component: () => import('../views/admin/AdminHomeView.vue') },
      { path: 'categories', name: 'admin-categories', component: () => import('../views/admin/AdminCategoriesView.vue') },
      { path: 'products', name: 'admin-products', component: () => import('../views/admin/AdminProductsView.vue') },
      { path: 'orders', name: 'admin-orders', component: () => import('../views/admin/AdminOrdersView.vue') },
      { path: 'logs', name: 'admin-operation-logs', component: () => import('../views/admin/OperationLogsView.vue') },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/pc' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore(pinia)
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    if (to.path.startsWith('/admin')) {
      return { name: 'admin-login' }
    }
    const loginName = to.path.startsWith('/m') ? 'mobile-login' : 'pc-login'
    return { name: loginName, query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { name: 'admin-login', query: { reason: 'forbidden' } }
  }
  if (to.name === 'admin-login' && auth.isAdmin) {
    return { name: 'admin-home' }
  }
  if (to.name === 'mobile-login' && auth.isLoggedIn) {
    return { name: 'mobile-products' }
  }
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - E-Shop` : 'E-Shop'
})

export default router
