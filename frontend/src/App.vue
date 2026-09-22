<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from './api'

const route = useRoute()
const router = useRouter()
const publicPage = computed(() => ['/login', '/register', '/forgot-password'].includes(route.path) || route.path.startsWith('/s/'))
async function logout() { await api('/api/user/logout'); router.push('/login') }
</script>

<template>
  <div class="shell" :class="{ simple: publicPage }">
    <aside v-if="!publicPage" class="sidebar">
      <RouterLink class="brand" to="/files"><span class="brand-mark">◆</span> Cloud Disk</RouterLink>
      <nav aria-label="主导航">
        <RouterLink to="/files">文件与 AI</RouterLink>
        <RouterLink to="/recycle">回收站</RouterLink>
        <RouterLink to="/shares">我的分享</RouterLink>
        <RouterLink to="/profile">个人中心</RouterLink>
      </nav>
      <button class="subtle logout" @click="logout">退出登录</button>
    </aside>
    <main class="main"><RouterView /></main>
  </div>
</template>
