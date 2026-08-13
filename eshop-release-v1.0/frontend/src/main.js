import { createApp } from 'vue'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'vant/es/toast/style'
import 'vant/es/dialog/style'
import 'vant/es/image-preview/style'
import './styles/global.css'
import './styles/portal-theme.css'
import './styles/mobile-market.css'
import App from './App.vue'
import { pinia } from './pinia'
import router from './router'
import { initializeNativeRuntime } from './native/runtime'

// Android 构建在 Vue 首次绘制前就带上平台标记，避免启动时先闪现 H5 布局。
if (import.meta.env.VITE_APP_PLATFORM === 'android') {
  document.documentElement.classList.add('is-native-app')
}

const app = createApp(App)
  .use(pinia)
  .use(router)

app.mount('#app')

router.isReady().then(() => initializeNativeRuntime(router))
