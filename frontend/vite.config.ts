import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: true,          // 监听 0.0.0.0，允许同一局域网内其它设备访问
    port: 5173,
    // 放行任意 Host（配合 trycloudflare / ngrok 等内网穿透的随机公网域名，
    // 否则 Vite 会以 403 拒绝非 localhost 的访问请求）
    allowedHosts: true,
    proxy: {
      // 前端请求 /api → 后端 8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // WebSocket 实时连接代理
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true
      }
    }
  }
})
