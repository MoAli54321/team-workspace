// Globale Styles gelten für die gesamte Anwendung und laden auch die Basis-Styles.
import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// Pinia wird vor dem Router eingebunden, damit dessen Guard den Auth-Store bereits verwenden kann.
// App.vue stellt anschließend den Platz für die zur URL passende Seite bereit.
createApp(App).use(createPinia()).use(router).mount('#app')
