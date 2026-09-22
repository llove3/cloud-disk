<script setup>
import { computed, reactive, ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '../api'

const props = defineProps({ mode: { type: String, required: true } })
const router = useRouter()
const form = reactive({ email: '', password: '', newPassword: '', code: '' })
const message = ref('')
const busy = ref(false)
const sending = ref(false)
const showPassword = ref(false)
const seconds = ref(0)
const messageType = ref('info')
let timer
onUnmounted(() => window.clearInterval(timer))
const title = computed(() => ({ login: '欢迎回来', register: '创建账号', forgot: '重置密码' })[props.mode])
async function sendCode() {
  if (!form.email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email)) { message.value = '请先填写有效邮箱'; messageType.value = 'error'; return }
  sending.value = true
  try {
    message.value = await post('/api/user/send-code', { email: form.email })
    messageType.value = message.value.includes('已发送') ? 'success' : 'error'
    if (messageType.value === 'success') { seconds.value = 60; timer = window.setInterval(() => { if (--seconds.value <= 0) window.clearInterval(timer) }, 1000) }
  } catch { message.value = '网络故障，验证码发送失败，请重试'; messageType.value = 'error' }
  finally { sending.value = false }
}
async function submit() {
  message.value = ''
  if (!form.email || (props.mode !== 'forgot' && !form.password) || (props.mode === 'forgot' && !form.newPassword) || (props.mode !== 'login' && !form.code)) { message.value = '请填写所有必填项'; messageType.value = 'error'; return }
  busy.value = true
  try {
    const path = props.mode === 'forgot' ? 'forgot-password' : props.mode
    const fields = props.mode === 'forgot'
      ? { email: form.email, newPassword: form.newPassword, code: form.code }
      : props.mode === 'register'
        ? { email: form.email, password: form.password, code: form.code }
        : { email: form.email, password: form.password }
    const result = await post(`/api/user/${path}`, fields)
    message.value = result
    messageType.value = result.includes('成功') ? 'success' : 'error'
    if (messageType.value === 'success') router.push(props.mode === 'login' ? '/files' : '/login')
  } catch { message.value = '网络故障，请检查连接后重试'; messageType.value = 'error' }
  finally { busy.value = false }
}
</script>

<template>
  <div class="auth-wrap">
    <section class="auth-card">
      <div class="brand"><span class="brand-mark">✿</span> Cloud Disk</div>
      <h1>{{ title }}</h1>
      <p class="muted">给文件一个好找的家，让灵感随时发芽。</p>
      <form @submit.prevent="submit">
        <label>邮箱<input v-model.trim="form.email" type="email" required autocomplete="email" /></label>
        <label v-if="mode !== 'forgot'">密码<div class="password-field"><input v-model="form.password" :type="showPassword ? 'text' : 'password'" required :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" /><button type="button" class="subtle" @click="showPassword = !showPassword">{{ showPassword ? '隐藏' : '显示' }}</button></div></label>
        <label v-if="mode === 'forgot'">新密码<input v-model="form.newPassword" type="password" required autocomplete="new-password" /></label>
        <label v-if="mode !== 'login'">邮箱验证码<div class="inline"><input v-model.trim="form.code" required /><button type="button" class="secondary" :disabled="sending || seconds > 0" @click="sendCode">{{ seconds > 0 ? `${seconds} 秒后重发` : sending ? '发送中…' : '发送验证码' }}</button></div></label>
        <button class="primary full" :disabled="busy">{{ busy ? '请稍候…' : title }}</button>
      </form>
      <p v-if="message" class="notice" :class="messageType" role="status">{{ message }}</p>
      <div class="auth-links"><RouterLink to="/login">登录</RouterLink><RouterLink to="/register">注册</RouterLink><RouterLink to="/forgot-password">找回密码</RouterLink></div>
    </section>
  </div>
</template>
