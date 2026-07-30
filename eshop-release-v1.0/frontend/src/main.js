import { createApp } from 'vue'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'vant/es/toast/style'
import 'vant/es/dialog/style'
import 'vant/es/image-preview/style'
import './styles/global.css'
import App from './App.vue'
import { pinia } from './pinia'
import router from './router'

createApp(App)
  .use(pinia)
  .use(router)
  .mount('#app')
