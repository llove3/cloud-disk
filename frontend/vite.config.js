import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: { outDir: '../src/main/resources/static', emptyOutDir: true },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '^/s/[^/]+/(verify|ai/chat)$': 'http://localhost:8080',
      '/avatars': 'http://localhost:8080'
    }
  }
})
