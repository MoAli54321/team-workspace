// Globale Styles gelten für die gesamte Anwendung und laden auch die Basis-Styles.
import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

// Startet Vue mit App.vue als Hauptkomponente im Element #app aus index.html.
createApp(App).use(router).mount('#app')
