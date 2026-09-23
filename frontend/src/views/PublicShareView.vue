<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import MarkdownText from '../components/MarkdownText.vue'

const code = useRoute().params.code
const password = ref('')
const message = ref('')
const summary = ref(null)
const loading = ref(false)
const previewUrl = ref('')

async function fileBlob() {
  const response = await fetch(`/s/${encodeURIComponent(code)}/verify`, {
    method: 'POST', body: new URLSearchParams({ password: password.value })
  })
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
    const filename = decodeURIComponent(response.headers.get('content-disposition')?.match(/filename\*=UTF-8''([^;]+)/)?.[1] || 'shared-file')
    if (!/\.(pdf|txt|md|png|jpe?g|gif|webp)$/i.test(filename)) {
      message.value = '浏览器暂不支持预览此格式，请使用下载。'
      return
    }
    if (previewUrl.value) window.URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = window.URL.createObjectURL(await response.blob())
  } catch (error) { message.value = error.message }
}
async function showSummary() {
  summary.value = null
  message.value = ''
  loading.value = true
  try {
    const response = await fetch(`/s/${encodeURIComponent(code)}/summary`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: password.value })
    })
    if (!response.ok) {
      const body = await response.text()
      let reason = body
      try { reason = JSON.parse(body).message || JSON.parse(body).detail || body } catch { /* plain response */ }
      throw new Error(reason || '无法查看摘要')
    }
    summary.value = await response.json()
  } catch (error) { message.value = error.message }
  finally { loading.value = false }
}
</script>

<template>
  <div class="auth-wrap share-wrap"><section class="auth-card wide">
    <div class="brand"><span class="brand-mark">✿</span> Cloud Disk</div>
    <p class="eyebrow">A FILE FOR YOU</p><h1>收到一份文件分享</h1>
    <p class="muted">分享码 {{ code }}。输入提取码后可下载、预览或查看内容摘要。每次操作计入一次访问。</p>
    <p class="muted">查看摘要时，可提取的文档文字会发送给 DeepSeek；图片等格式仅列出名称。</p>
    <label>提取码<input v-model="password" placeholder="如未设置可留空" /></label>
    <div class="inline wrap">
      <button class="primary" @click="download">验证并下载</button>
      <button class="secondary" @click="preview">预览文件</button>
      <button class="secondary" :disabled="loading" @click="showSummary">{{ loading ? '正在整理摘要…' : '查看摘要' }}</button>
    </div>
    <p v-if="message" class="notice" role="status">{{ message }}</p>
    <section v-if="summary" class="share-answer" role="status">
      <h2>{{ summary.fileName }}</h2>
      <p v-if="summary.notice" class="notice">{{ summary.notice }}</p>
      <MarkdownText v-if="summary.summary" :text="summary.summary" />
      <div v-if="summary.isPackage && summary.entries.length">
        <h3>压缩包文件清单</h3>
        <ul><li v-for="entry in summary.entries" :key="entry.name">{{ entry.name }} · {{ entry.type }}<span v-if="entry.reason" class="muted"> · {{ entry.reason }}</span></li></ul>
      </div>
    </section>
  </section></div>
  <div v-if="previewUrl" class="modal-backdrop" @click.self="previewUrl = ''"><section class="preview-modal"><div class="inline"><h2>分享文件预览</h2><button class="subtle" @click="previewUrl = ''">关闭</button></div><iframe :src="previewUrl" title="分享文件预览"></iframe></section></div>
</template>
