import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { ensureAuthToken } from './utils/autoLogin'

// Vant 样式（按需引入会更快，但全量引入开发期更方便）
import 'vant/lib/index.css'
import Vant from 'vant'
import './style.css'

const app = createApp(App)
app.use(router)
app.use(Vant)

// 联调期间：先尝试自动登录拿到 JWT，再挂载应用
ensureAuthToken().finally(() => {
  app.mount('#app')
})