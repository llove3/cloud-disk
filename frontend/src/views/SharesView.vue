<script setup>
import { onMounted, ref } from 'vue'
import { api, post } from '../api'
import AppDialog from '../components/AppDialog.vue'

const shares = ref([])
const message = ref('')
const logs = ref([])
const logShare = ref(null)
const dialog = ref('')
const active = ref(null)
const form = ref({ password: '', expireDays: 0, maxVisits: 0 })
async function load() { try { shares.value = await api('/api/share/list') || [] } catch (error) { message.value = error.message } }
onMounted(load)
async function remove(share) {
  active.value = share; dialog.value = 'remove'
}
async function edit(share) {
  active.value = share
  form.value = { password: share.password || '', expireDays: 0, maxVisits: share.maxVisits || 0 }
  dialog.value = 'edit'
}
async function saveEdit() {
  try { message.value = await post('/api/share/update', { shareId: active.value.id, ...form.value }); await load(); dialog.value = '' }
  catch (error) { message.value = error.message }
}
async function confirmDialog() {
  if (dialog.value === 'remove') {
    try { message.value = await post('/api/share/delete', { shareId: active.value.id }); await load(); dialog.value = '' }
    catch (error) { message.value = error.message }
  } else await saveEdit()
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
  <AppDialog v-if="dialog" :title="dialog === 'edit' ? '编辑分享' : '取消分享'" @close="dialog = ''" @confirm="confirmDialog"><template v-if="dialog === 'edit'"><label>提取码<input v-model="form.password" placeholder="留空取消提取码" /></label><label>从现在起有效天数<input v-model.number="form.expireDays" type="number" min="0" /></label><label>最多访问次数<input v-model.number="form.maxVisits" type="number" min="0" /></label></template><p v-else class="muted">确定取消 {{ active?.fileName || active?.shareCode }} 的分享吗？</p></AppDialog>
</template>
