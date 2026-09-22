<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { stream } from '../api'

const code = useRoute().params.code
const password = ref('')
const message = ref('')
const question = ref('')
const answer = ref('')
const sources = ref([])
const asking = ref(false)
const previewUrl = ref('')
async function fileBlob() {
  const response = await fetch(`/s/${encodeURIComponent(code)}/verify`, { method: 'POST', body: new URLSearchParams({ password: password.value }) })
  if (!response.ok) throw new Error(await response.text())
  return response
}
async function download() {
  try {
    const response = await fileBlob()
    const blob = await response.blob()
    const link = document.createElement('a')
    link.href = window.URL.createObjectURL(blob)
    link.download = decodeURIComponent(response.headers.get('content-disposition')?.match(/filename\*=UTF-8''([^;]+)/)?.[1] || 'shared-file')
    link.click()
    window.URL.revokeObjectURL(link.href)
    message.value = '下载已开始'
  } catch (error) { message.value = error.message }
}
async function preview() {
  try {
    const response = await fileBlob()
    if (previewUrl.value) window.URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = window.URL.createObjectURL(await response.blob())
  } catch (error) { message.value = error.message }
}
async function ask() {
  if (!question.value.trim()) return
  answer.value = ''
  sources.value = []
  message.value = ''
  asking.value = true
  try {
    await stream(`/s/${encodeURIComponent(code)}/ai/chat`, question.value.trim(), (event, data) => {
      if (event === 'source') sources.value.push(data)
      if (event === 'token') answer.value += data
      if (event === 'error') message.value = data
    }, password.value)
  } catch (error) { message.value = error.message }
  finally { asking.value = false }
}
</script>

<template>
  <div class="auth-wrap share-wrap"><section class="auth-card wide"><div class="brand"><span class="brand-mark">◆</span> Cloud Disk</div><p class="eyebrow">SHARED FILE</p><h1>收到一份文件分享</h1><p class="muted">分享码 {{ code }}。输入提取码后可下载文件，单文件文档还可以提问。</p><label>提取码<input v-model="password" placeholder="如未设置可留空" /></label><div class="inline"><button class="primary" @click="download">验证并下载</button><button class="secondary" @click="preview">预览文件</button></div><p v-if="message" class="notice" role="status">{{ message }}</p><div class="share-ai"><h2>就这份文档提问</h2><p class="muted">每次提问都会计入分享访问次数，命中的片段会发送给 DeepSeek。</p><form class="inline" @submit.prevent="ask"><input v-model="question" placeholder="这份文档说了什么？" maxlength="1000" /><button class="primary" :disabled="asking">{{ asking ? '回答中…' : '提问' }}</button></form><p v-if="answer" class="answer">{{ answer }}</p><div v-if="sources.length" class="sources"><button v-for="source in sources" :key="source.number" class="source" @click="preview">[{{ source.number }}] {{ source.fileName }} · v{{ source.version }}<small>{{ source.snippet }}</small></button></div></div></section></div>
  <div v-if="previewUrl" class="modal-backdrop" @click.self="previewUrl = ''"><section class="preview-modal"><div class="inline"><h2>分享文件预览</h2><button class="subtle" @click="previewUrl = ''">关闭</button></div><iframe :src="previewUrl" title="分享文件预览"></iframe></section></div>
</template>
