<script setup>
import { onMounted, ref } from 'vue'
import { api, post } from '../api'
import AppDialog from '../components/AppDialog.vue'

const files = ref([])
const message = ref('')
const deleting = ref(null)
async function load() { try { files.value = await api('/api/recycle/list') || [] } catch (error) { message.value = error.message } }
onMounted(load)
async function act(file, action) {
  if (action === 'permanent') { deleting.value = file; return }
  try { message.value = await post(`/api/recycle/${action}`, { fileId: file.id }); await load() }
  catch (error) { message.value = error.message }
}
async function confirmDelete() {
  try { message.value = await post('/api/recycle/permanent', { fileId: deleting.value.id }); deleting.value = null; await load() }
  catch (error) { message.value = error.message }
}
</script>

<template>
  <div class="page-head"><div><p class="eyebrow">RECYCLE BIN</p><h1>回收站</h1><p class="muted">恢复误删文件，或彻底清理空间。</p></div></div>
  <p v-if="message" class="notice" role="status">{{ message }}</p>
  <section class="panel table-wrap"><table><thead><tr><th>名称</th><th>大小</th><th>删除时间</th><th>操作</th></tr></thead><tbody><tr v-for="file in files" :key="file.id"><td>{{ file.fileName }}</td><td>{{ file.fileSize }} B</td><td>{{ file.deletedAt || '—' }}</td><td class="actions"><button @click="act(file, 'restore')">恢复</button><button class="danger-text" @click="act(file, 'permanent')">彻底删除</button></td></tr><tr v-if="!files.length"><td colspan="4" class="empty">回收站为空。</td></tr></tbody></table></section>
  <AppDialog v-if="deleting" title="彻底删除文件" confirm-text="彻底删除" @close="deleting = null" @confirm="confirmDelete"><p class="muted">确定彻底删除 {{ deleting.fileName }}？此操作无法撤销。</p></AppDialog>
</template>
