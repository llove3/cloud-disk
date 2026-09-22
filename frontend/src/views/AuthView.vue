<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '../api'

const props = defineProps({ mode: { type: String, required: true } })
const router = useRouter()
const form = reactive({ email: '', password: '', newPassword: '', code: '' })
const message = ref('')
const busy = ref(false)
const title = computed(() => ({ login: '欢迎回来', register: '创建账号', forgot: '重置密码' })[props.mode])
async function sendCode() {
  try { message.value = await post('/api/user/send-code', { email: form.email }) }
  catch (error) { message.value = error.message }
}
async function submit() {
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
    if (result.includes('成功')) router.push(props.mode === 'login' ? '/files' : '/login')
  } catch (error) { message.value = error.message }
  finally { busy.value = false }
}
</script>

<template>
  <div class="auth-wrap">
    <section class="auth-card">
      <div class="brand"><span class="brand-mark">◆</span> Cloud Disk</div>
      <h1>{{ title }}</h1>
      <p class="muted">你的文件与知识，都在一个安全的空间。</p>
      <form @submit.prevent="submit">
        <label>邮箱<input v-model.trim="form.email" type="email" required autocomplete="email" /></label>
        <label v-if="mode !== 'forgot'">密码<input v-model="form.password" type="password" required :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" /></label>
        <label v-if="mode === 'forgot'">新密码<input v-model="form.newPassword" type="password" required autocomplete="new-password" /></label>
        <label v-if="mode !== 'login'">邮箱验证码<div class="inline"><input v-model.trim="form.code" required /><button type="button" class="secondary" @click="sendCode">发送验证码</button></div></label>
        <button class="primary full" :disabled="busy">{{ busy ? '请稍候…' : title }}</button>
      </form>
      <p v-if="message" class="notice" role="status">{{ message }}</p>
      <div class="auth-links"><RouterLink to="/login">登录</RouterLink><RouterLink to="/register">注册</RouterLink><RouterLink to="/forgot-password">找回密码</RouterLink></div>
    </section>
  </div>
</template>
