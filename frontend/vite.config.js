import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],

  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },

  server: {
    host: '0.0.0.0',
    port: 3000,
    allowedHosts: [
      'zreo.top',
      '.zreo.top',
      'localhost',
      '127.0.0.1',
      '*.trycloudflare.com',
      'x6b479d9.natappfree.cc',  // 允许这个域名
      '.natappfree.cc'            // 或者允许所有 natappfree.cc 的子域名
    ],
    cors: true,
    strictPort: true,
    hmr: false,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },

  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
      },
    },
    rollupOptions: {
      output: {
        manualChunks: {
          echarts: ['echarts', 'vue-echarts'],
          marked: ['marked'],
          vendor: ['vue', 'vue-router', 'pinia', 'axios'],
        },
      },
    },
  },

  css: {
    postcss: './postcss.config.js',
  },
})
