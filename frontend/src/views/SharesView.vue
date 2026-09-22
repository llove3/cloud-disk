<script setup>
import { onMounted, ref } from 'vue'
import { api, post } from '../api'

const shares = ref([])
const message = ref('')
const logs = ref([])
const logShare = ref(null)
async function load() { try { shares.value = await api('/api/share/list') || [] } catch (error) { message.value = error.message } }
onMounted(load)
async function remove(share) {
  if (!window.confirm(`取消分享 ${share.fileName || share.shareCode}？`)) return
  try { message.value = await post('/api/share/delete', { shareId: share.id }); await load() }
  catch (error) { message.value = error.message }
}
async function edit(share) {
  const password = window.prompt('新提取码（留空表示取消提取码）', share.password || '')
  if (password === null) return
  const expireDays = window.prompt('从现在起有效天数（0 表示不限）', '0')
  if (expireDays === null) return
  const maxVisits = window.prompt('最大访问次数（0 表示不限）', String(share.maxVisits || 0))
  if (maxVisits === null) return
  try { message.value = await post('/api/share/update', { shareId: share.id, password, expireDays, maxVisits }); await load() }
  catch (error) { message.value = error.message }
}
async function viewLogs(share) {
  try { logShare.value = share; logs.value = await api(`/api/share/access-logs?shareId=${share.id}`) || [] }
  catch (error) { message.value = error.message }
}
function copy(share) { window.navigator.clipboard.writeText(`${window.location.origin}/s/${share.shareCode}`); message.value = '链接已复制' }
</script>

<template>
  <div class="page-head"><div><p class="eyebrow">SHARING</p><h1>我的分享</h1><p class="muted">管理提取码、有效期与访问记录。</p></div></div>
  <p v-if="message" class="notice" role="status">{{ message }}</p>
  <section class="panel table-wrap"><table><thead><tr><th>文件</th><th>分享链接</th><th>访问</th><th>到期</th><th>操作</th></tr></thead><tbody><tr v-for="share in shares" :key="share.id"><td>{{ share.fileName || (share.isPackage ? '打包分享' : `文件 ${share.fileId}`) }}</td><td><a :href="`/s/${share.shareCode}`" target="_blank" rel="noopener">{{ share.shareCode }}</a><small v-if="share.password" class="muted">提取码：{{ share.password }}</small></td><td>{{ share.visitCount || 0 }} / {{ share.maxVisits || '不限' }}</td><td>{{ share.expireTime || '不限' }}</td><td class="actions"><button @click="copy(share)">复制</button><button @click="edit(share)">编辑</button><button @click="viewLogs(share)">日志</button><button class="danger-text" @click="remove(share)">取消</button></td></tr><tr v-if="!shares.length"><td colspan="5" class="empty">还没有分享。</td></tr></tbody></table></section>
  <section v-if="logShare" class="panel"><div class="inline"><h2>访问日志 · {{ logShare.shareCode }}</h2><button class="subtle" @click="logShare = null">关闭</button></div><div v-for="log in logs" :key="log.id" class="version-row"><span>{{ log.createdAt || log.accessTime }} · {{ log.ipAddress }}</span><span>{{ log.success ? '成功' : log.errorReason || '失败' }}</span></div><p v-if="!logs.length" class="muted">暂无访问记录。</p></section>
</template>
