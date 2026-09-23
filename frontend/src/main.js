import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import AuthView from './views/AuthView.vue'
import FilesView from './views/FilesView.vue'
import AiView from './views/AiView.vue'
import RecycleView from './views/RecycleView.vue'
import SharesView from './views/SharesView.vue'
import ProfileView from './views/ProfileView.vue'
import PublicShareView from './views/PublicShareView.vue'
import './style.css'

const router = createRouter({ history: createWebHistory(), routes: [
  { path: '/', redirect: '/files' },
  { path: '/login', component: AuthView, props: { mode: 'login' }, meta: { public: true } },
  { path: '/register', component: AuthView, props: { mode: 'register' }, meta: { public: true } },
  { path: '/forgot-password', component: AuthView, props: { mode: 'forgot' }, meta: { public: true } },
  { path: '/files', component: FilesView },
  { path: '/ai', component: AiView },
  { path: '/recycle', component: RecycleView },
  { path: '/shares', component: SharesView },
  { path: '/profile', component: ProfileView },
  { path: '/s/:code', component: PublicShareView, meta: { public: true } }
] })

router.beforeEach(async to => {
  if (to.meta.public) return true
  try {
    const response = await fetch('/api/user/info', { credentials: 'same-origin' })
    if (response.ok && await response.json()) return true
  } catch { /* server unavailable */ }
  return '/login'
})

createApp(App).use(router).mount('#app')
