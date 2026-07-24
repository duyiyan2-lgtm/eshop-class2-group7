import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// Vant 样式（按需引入会更快，但全量引入开发期更方便）
import 'vant/lib/index.css'
import Vant from 'vant'
import './style.css'

const app = createApp(App)
app.use(router)
app.use(Vant)
app.mount('#app')