<script setup>
import { onMounted, ref } from 'vue'
import { api, post } from '../api'

const user = ref(null)
const message = ref('')
const form = ref({ username: '', email: '', code: '', oldPassword: '', newPassword: '', days: 30 })
async function load() {
  try {
    user.value = await api('/api/user/info')
    form.value.username = user.value.username || ''
    form.value.email = user.value.email || ''
    form.value.days = user.value.recycleRetentionDays || 30
  } catch (error) { message.value = error.message }
}
onMounted(load)
async function change(path, fields) {
  try { message.value = await post(`/api/user/${path}`, fields); await load() }
  catch (error) { message.value = error.message }
}
async function avatar(event) {
  const file = event.target.files?.[0]
  if (!file) return
  const body = new FormData()
  body.append('avatar', file)
  try {
    const result = await api('/api/user/upload-avatar', { method: 'POST', body })
    message.value = result.startsWith('/avatars/') ? '头像已更新' : result
    await load()
  }
  catch (error) { message.value = error.message }
}
</script>

<template>
  <div class="page-head"><div><p class="eyebrow">ACCOUNT</p><h1>个人中心</h1><p class="muted">管理账号、安全设置与存储空间。</p></div></div>
  <p v-if="message" class="notice" role="status">{{ message }}</p>
  <div v-if="user" class="profile-grid"><section class="panel"><h2>账号信息</h2><div class="avatar-line"><img v-if="user.avatar" :src="user.avatar" alt="头像" /><span v-else class="avatar-fallback">{{ user.username?.slice(0, 1) || 'U' }}</span><label class="secondary upload">上传头像<input type="file" accept=".jpg,.jpeg,.png,.gif" @change="avatar" /></label></div><p class="muted">选择后自动保存；支持 JPG、PNG、GIF，最大 2 MB。</p><p>邮箱：{{ user.email }}</p><p>已用空间：{{ user.usedSpace }} / {{ user.totalSpace }} B</p><button class="subtle" @click="change('recalculate-space', {})">校准空间</button></section><section class="panel"><h2>修改用户名</h2><form class="stack" @submit.prevent="change('update-username', { newUsername: form.username })"><input v-model.trim="form.username" required maxlength="20" /><button class="secondary">保存用户名</button></form></section><section class="panel"><h2>换绑邮箱</h2><form class="stack" @submit.prevent="change('update-email', { newEmail: form.email, code: form.code })"><input v-model.trim="form.email" type="email" required /><div class="inline"><input v-model.trim="form.code" placeholder="邮箱验证码" required /><button type="button" class="subtle" @click="change('send-code', { email: form.email })">发送验证码</button></div><button class="secondary">保存邮箱</button></form></section><section class="panel"><h2>修改密码</h2><form class="stack" @submit.prevent="change('change-password', { oldPassword: form.oldPassword, newPassword: form.newPassword })"><input v-model="form.oldPassword" type="password" placeholder="当前密码" required /><input v-model="form.newPassword" type="password" placeholder="新密码" required /><button class="secondary">修改密码</button></form></section><section class="panel"><h2>回收站保留时间</h2><form class="inline" @submit.prevent="change('update-recycle-retention', { days: form.days })"><input v-model.number="form.days" type="number" min="1" max="365" /><span>天</span><button class="secondary">保存</button></form></section></div>
</template>
