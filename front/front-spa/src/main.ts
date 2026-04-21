import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './ui/App.vue'
import router from './ui/router'
import { setAppRouter } from './ui/navigation'

setAppRouter(router)
createApp(App).use(router).use(ElementPlus).mount('#app')
