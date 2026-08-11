import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver, VantResolver } from 'unplugin-vue-components/resolvers'

const stripVantIconRemoteFallback = {
  name: 'strip-vant-icon-remote-fallback',
  enforce: 'pre',
  transform(code, id) {
    const normalizedId = id.replace(/\\/g, '/')
    if (!normalizedId.includes('/vant/es/icon/index.css') || !code.includes('at.alicdn.com')) {
      return null
    }

    return {
      code: code.replace(
        /,url\(\/\/at\.alicdn\.com\/[^)]+\)\s*format\(["']woff["']\)/g,
        '',
      ),
      map: null,
    }
  },
}

export default defineConfig({
  plugins: [
    vue(),
    stripVantIconRemoteFallback,
    Components({
      resolvers: [
        ElementPlusResolver({ importStyle: 'css' }),
        VantResolver(),
      ],
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
