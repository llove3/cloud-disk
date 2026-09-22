<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from './api'

const route = useRoute()
const router = useRouter()
const publicPage = computed(() => ['/login', '/register', '/forgot-password'].includes(route.path) || route.path.startsWith('/s/'))
const user = ref(null)
async function loadUser() { if (!publicPage.value) { try { user.value = await api('/api/user/info') } catch { user.value = null } } }
watch(() => route.path, loadUser, { immediate: true })
onMounted(() => window.addEventListener('profile-updated', loadUser))
onUnmounted(() => window.removeEventListener('profile-updated', loadUser))
async function logout() { await api('/api/user/logout'); router.push('/login') }
</script>

<template>
  <div class="shell" :class="{ simple: publicPage }">
    <aside v-if="!publicPage" class="sidebar">
      <RouterLink class="brand" to="/files"><span class="brand-mark">✿</span> Cloud Disk</RouterLink>
      <nav aria-label="主导航">
        <RouterLink to="/files">文件与 AI</RouterLink>
        <RouterLink to="/shares">我的分享</RouterLink>
      </nav>
      <svg class="sidebar-flora" viewBox="0 0 140 120" fill="none" aria-hidden="true"><path d="M69 111C72 74 75 39 106 8M71 89C43 80 24 59 17 36M73 72C95 67 111 53 119 32" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/><path d="M104 9C85 10 80 23 83 39C101 34 107 23 104 9ZM17 36C34 29 46 36 49 51C33 55 22 48 17 36ZM119 32C102 28 92 38 95 53C109 53 117 44 119 32ZM69 84C49 70 38 77 41 92C55 98 65 93 69 84Z" fill="#c5dcb9" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M84 21C88 28 91 31 94 33M26 41C35 43 40 46 45 50M100 45C106 43 110 40 115 36" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/></svg>
      <button class="subtle logout" @click="logout">退出登录</button>
    </aside>
    <main class="main"><header v-if="!publicPage" class="topbar"><span class="muted">给文件一个好找的家</span><span class="spacer"></span><RouterLink class="recycle-shortcut" to="/recycle" title="回收站" aria-label="打开回收站">♻ <span>误删文件？到这里找回</span></RouterLink><RouterLink class="profile-shortcut" to="/profile"><img v-if="user?.avatar" :src="user.avatar" alt="" /><span v-else class="avatar-fallback">{{ user?.username?.slice(0, 1) || '我' }}</span>个人中心</RouterLink></header><RouterView /></main>
  </div>
</template>
