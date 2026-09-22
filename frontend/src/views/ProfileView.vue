<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, post } from '../api'
import AppDialog from '../components/AppDialog.vue'

const router = useRouter()
const user = ref(null)
const message = ref('')
const dialog = ref('')
const form = ref({ username: '', email: '', code: '', newPassword: '', days: 30 })
const seconds = ref(0)
let timer
function countdown() { seconds.value = 60; window.clearInterval(timer); timer = window.setInterval(() => { if (--seconds.value <= 0) window.clearInterval(timer) }, 1000) }
onUnmounted(() => window.clearInterval(timer))
async function load() {
  try {
    user.value = await api('/api/user/info')
    form.value.username = user.value.username || ''
    form.value.email = user.value.email || ''
    form.value.days = user.value.recycleRetentionDays || 30
    window.dispatchEvent(new window.Event('profile-updated'))
  } catch (error) { message.value = error.message }
}
onMounted(load)
async function change(path, fields) {
  try {
    const result = await post(`/api/user/${path}`, fields)
    message.value = result
    if (result.includes('成功')) { dialog.value = ''; await load() }
  } catch (error) { message.value = error.message }
}
async function sendPasswordCode() {
  try { const result = await post('/api/user/send-password-code', {}); message.value = result; if (result.includes('已发送')) countdown() }
  catch (error) { message.value = error.message }
}
async function save() {
  if (dialog.value === 'username') await change('update-username', { newUsername: form.value.username })
  if (dialog.value === 'email') await change('update-email', { newEmail: form.value.email, code: form.value.code })
  if (dialog.value === 'retention') await change('update-recycle-retention', { days: form.value.days })
  if (dialog.value === 'password') {
    try {
      const result = await post('/api/user/change-password', { newPassword: form.value.newPassword, code: form.value.code })
      message.value = result
      if (result.includes('成功')) router.push('/login')
    } catch (error) { message.value = error.message }
  }
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
  } catch (error) { message.value = error.message }
  finally { event.target.value = '' }
}
</script>

<template>
  <div class="page-head"><div><p class="eyebrow">MY ACCOUNT</p><h1>个人中心</h1><p class="muted">你的资料和空间，一目了然。</p></div></div>
  <p v-if="message" class="notice" role="status">{{ message }}</p>
  <div v-if="user" class="profile-grid"><section class="panel"><h2>我的资料</h2><div class="avatar-line"><img v-if="user.avatar" :src="user.avatar" alt="头像" /><span v-else class="avatar-fallback">{{ user.username?.slice(0, 1) || '我' }}</span><label class="secondary upload">更换头像<input type="file" accept=".jpg,.jpeg,.png,.gif" @change="avatar" /></label></div><p class="muted">选择图片后自动保存，最大 2 MB。</p><p><strong>用户名</strong> {{ user.username }} <button class="link" @click="dialog = 'username'">修改</button></p><p><strong>邮箱</strong> {{ user.email }} <button class="link" @click="dialog = 'email'">换绑</button></p><button class="secondary" @click="dialog = 'password'; form.code = ''">修改密码</button></section><section class="panel"><h2>存储空间</h2><p>已使用 {{ (user.usedSpace / 1048576).toFixed(1) }} MB / {{ (user.totalSpace / 1048576).toFixed(1) }} MB</p><div class="space-track"><span :style="{ width: `${Math.min(100, user.usedSpace / user.totalSpace * 100 || 0)}%` }"></span></div><p>回收站保留 {{ user.recycleRetentionDays || 30 }} 天 <button class="link" @click="dialog = 'retention'">调整</button></p><button class="subtle" @click="change('recalculate-space', {})">校准空间用量</button></section></div>
  <AppDialog v-if="dialog" :title="({ username: '修改用户名', email: '换绑邮箱', password: '修改密码', retention: '回收站保留时间' })[dialog]" @close="dialog = ''" @confirm="save"><template v-if="dialog === 'username'"><label>用户名<input v-model.trim="form.username" required maxlength="20" /></label></template><template v-else-if="dialog === 'email'"><label>新邮箱<input v-model.trim="form.email" type="email" required /></label><label>新邮箱验证码<input v-model.trim="form.code" required /></label><button type="button" class="secondary" @click="change('send-code', { email: form.email })">向新邮箱发送验证码</button></template><template v-else-if="dialog === 'password'"><p class="muted">验证码只发送到当前绑定邮箱：{{ user.email }}。5 分钟内有效，成功后需重新登录。</p><label>新密码<input v-model="form.newPassword" type="password" required /></label><label>邮箱验证码<input v-model.trim="form.code" required /></label><button type="button" class="secondary" :disabled="seconds > 0" @click="sendPasswordCode">{{ seconds > 0 ? `${seconds} 秒后可重发` : '发送验证码' }}</button></template><template v-else><label>保留天数<input v-model.number="form.days" type="number" min="1" max="365" required /></label></template></AppDialog>
</template>
