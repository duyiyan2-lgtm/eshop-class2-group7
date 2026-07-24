import { createApp } from 'vue'
import './styles/global.css'
import App from './App.vue'
import { pinia } from './pinia'
import router from './router'

createApp(App)
  .use(pinia)
  .use(router)
  .mount('#app')
