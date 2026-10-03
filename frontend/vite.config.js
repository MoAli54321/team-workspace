import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  // Verarbeitet Vue-Komponenten mit ihren <script>-, <template>- und <style>-Blöcken.
  plugins: [vue()],

  resolve: {
    alias: {
      // Das Import-Kürzel @ verweist unabhängig vom Arbeitsverzeichnis auf src.
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },

  server: {
    // Nur im Entwicklungsserver: /api-Aufrufe gehen an das Spring-Boot-Backend.
    // So kann das Frontend relative URLs verwenden, obwohl das Backend auf Port 8081 läuft.
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        // Passt den Host-Header der weitergeleiteten Anfrage an das Backend an.
        changeOrigin: true,
      },
    },
  },
})
