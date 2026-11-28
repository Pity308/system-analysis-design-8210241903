import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 员工端是纯静态资源，接口全部走 /api 前缀，开发期由 Vite 代理到本地后端，
// 生产期由 nginx 反代到同一个后端容器（报告 6.14.1 的部署形态）。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
