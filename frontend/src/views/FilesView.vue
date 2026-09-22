<script setup>
import { onMounted, ref, watch } from 'vue'
import { api, post, stream } from '../api'

const files = ref([])
const parentId = ref(0)
const trail = ref([{ id: 0, name: '全部文件' }])
const selected = ref([])
const active = ref(null)
const versions = ref([])
const indexStatus = ref(null)
const category = ref('')
const sortBy = ref('name')
const order = ref('asc')
const searchText = ref('')
const searchMode = ref('name')
const hits = ref([])
const message = ref('')
const busy = ref(false)
const shareOpen = ref(false)
const shareForm = ref({ password: '', expireDays: '', maxVisits: '' })
const shareUrl = ref('')
const aiOpen = ref(false)
const question = ref('')
const asking = ref(false)
const turns = ref([])
const previewUrl = ref('')
const isFolder = file => file?.folder === true
const showVersions = ref(false)
const bytes = value => value == null ? '—' : value < 1024 ? `${value} B` : value < 1048576 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1048576).toFixed(1)} MB`

async function load() {
  try {
    const params = new URLSearchParams({ parentId: parentId.value, category: category.value, sortBy: sortBy.value, order: order.value })
    files.value = await api(`/api/file/list?${params}`) || []
    selected.value = []
  } catch (error) { message.value = error.message }
}
onMounted(load)
watch([category, sortBy, order], load)
function enter(file) { if (isFolder(file)) { parentId.value = file.id; trail.value.push({ id: file.id, name: file.fileName }); load() } else openPreview(file) }
function goTo(index) { parentId.value = trail.value[index].id; trail.value = trail.value.slice(0, index + 1); load() }
function toggle(id) { selected.value = selected.value.includes(id) ? selected.value.filter(item => item !== id) : [...selected.value, id] }
function all() { selected.value = selected.value.length === files.value.length ? [] : files.value.map(file => file.id) }
function openPreview(file) { previewUrl.value = `/api/file/preview/${file.id}` }
async function upload(event) {
  const items = [...event.target.files]
  if (!items.length) return
  busy.value = true
  try {
    for (const file of items) {
      const body = new FormData()
      body.append('file', file)
      body.append('parentId', parentId.value)
      const result = await api('/api/file/upload', { method: 'POST', body })
      if (!result.success) throw new Error(result.message)
    }
    message.value = '上传完成，文档正在索引'
    await load()
  } catch (error) { message.value = error.message }
  finally { busy.value = false; event.target.value = '' }
}
async function folder() {
  const folderName = window.prompt('文件夹名称')
  if (!folderName) return
  try { const result = await post('/api/file/folder/create', { folderName, parentId: parentId.value }); message.value = result.message; await load() }
  catch (error) { message.value = error.message }
}
async function run(path, fields, refresh = true) {
  try {
    const result = await post(path, fields)
    message.value = typeof result === 'string' ? result : result.message || '操作完成'
    if (refresh) await load()
    return result
  } catch (error) { message.value = error.message }
}
async function action(name, file) {
  active.value = file
  if (name === 'download') { window.location.href = `/api/file/download?fileId=${file.id}`; return }
  if (name === 'versions') { showVersions.value = true; versions.value = await api(`/api/file/versions?fileId=${file.id}`) || []; indexStatus.value = await api(`/api/ai/index/${file.id}`); return }
  if (name === 'share') { shareOpen.value = true; shareUrl.value = ''; return }
  if (name === 'rename') { const newName = window.prompt('新名称', file.fileName); if (newName) await run('/api/file/rename', { fileId: file.id, newName }); return }
  if (name === 'remark') { const remark = window.prompt('备注', file.remark || ''); if (remark !== null) await run('/api/file/update-remark', { fileId: file.id, remark }); return }
  if (name === 'move') { const targetParentId = window.prompt('目标文件夹 ID，根目录填 0', '0'); if (targetParentId !== null) await run('/api/file/move', { fileId: file.id, targetParentId }); return }
  if (name === 'star') await run('/api/file/toggle-star', { fileId: file.id })
  if (name === 'delete' && window.confirm(`移入回收站：${file.fileName}？`)) await run('/api/file/delete', { fileId: file.id })
}
async function batch(name) {
  if (!selected.value.length) return
  const ids = selected.value.join(',')
  if (name === 'download') { window.location.href = `/api/file/batch-download?fileIds=${ids}`; return }
  if (name === 'delete' && window.confirm(`将 ${selected.value.length} 个项目移入回收站？`)) await run('/api/file/batch-delete', { fileIds: ids })
  if (name === 'move') { const targetParentId = window.prompt('目标文件夹 ID，根目录填 0', '0'); if (targetParentId !== null) await run('/api/file/batch-move', { fileIds: ids, targetParentId }) }
  if (name === 'share') { shareOpen.value = true; active.value = null; shareUrl.value = '' }
}
async function createShare() {
  const fields = { password: shareForm.value.password, expireDays: shareForm.value.expireDays, maxVisits: shareForm.value.maxVisits }
  try {
    const result = active.value ? await post('/api/share/create', { ...fields, fileId: active.value.id })
      : await post('/api/file/create-package-share', { ...fields, fileIds: selected.value.join(',') })
    const share = result.share || result
    if (result.success === false) throw new Error(result.message)
    shareUrl.value = `${window.location.origin}/s/${share.shareCode}`
    message.value = '分享已创建'
  } catch (error) { message.value = error.message }
}
async function search() {
  try {
    if (!searchText.value.trim()) { hits.value = []; await load(); return }
    hits.value = searchMode.value === 'content'
      ? await api(`/api/ai/search?q=${encodeURIComponent(searchText.value)}`)
      : await api(`/api/file/search?keyword=${encodeURIComponent(searchText.value)}&category=${category.value}`)
  } catch (error) { message.value = error.message }
}
async function ask() {
  const text = question.value.trim()
  if (!text || asking.value) return
  const turn = { question: text, answer: '', sources: [], error: '' }
  turns.value.push(turn)
  question.value = ''
  asking.value = true
  try {
    await stream('/api/ai/chat', text, (event, data) => {
      if (event === 'source') turn.sources.push(data)
      if (event === 'token') turn.answer += data
      if (event === 'error') turn.error = data
    })
  } catch (error) { turn.error = error.message }
  finally { asking.value = false }
}
async function retryIndex() {
  try { indexStatus.value = await post(`/api/ai/index/${active.value.id}/retry`, {}); message.value = '已重新提交索引任务' }
  catch (error) { message.value = error.message }
}
async function refreshIndex() { indexStatus.value = await api(`/api/ai/index/${active.value.id}`) }
async function versionAction(name, version) {
  if (name === 'preview') { previewUrl.value = `/api/file/version/preview/${version.id}`; return }
  if (name === 'download') { window.location.href = `/api/file/version/download/${version.id}`; return }
  if (name === 'rollback' && window.confirm(`回滚到第 ${version.versionNumber} 版？`)) await run('/api/file/rollback', { fileId: active.value.id, version: version.versionNumber })
  if (name === 'delete' && window.confirm('删除此历史版本？')) await run('/api/file/version/delete', { fileId: active.value.id, versionId: version.id })
  versions.value = await api(`/api/file/versions?fileId=${active.value.id}`) || []
}
</script>

<template>
  <div class="page-head"><div><p class="eyebrow">WORKSPACE</p><h1>我的文件</h1><p class="muted">文件管理、版本控制与私有文档问答</p></div><button class="primary" @click="aiOpen = !aiOpen">✦ 问我的文档</button></div>
  <p v-if="message" class="notice" role="status">{{ message }}</p>
  <section class="panel toolbar">
    <div class="inline wrap"><label class="upload primary">{{ busy ? '上传中…' : '上传文件' }}<input type="file" multiple :disabled="busy" @change="upload" /></label><button class="secondary" @click="folder">新建文件夹</button><span class="spacer"></span><select v-model="category" aria-label="文件分类"><option value="">全部类型</option><option value="document">文档</option><option value="image">图片</option><option value="video">视频</option><option value="audio">音频</option><option value="archive">压缩包</option></select><select v-model="sortBy" aria-label="排序"><option value="name">按名称</option><option value="time">按时间</option><option value="size">按大小</option></select><button class="subtle" @click="order = order === 'asc' ? 'desc' : 'asc'">{{ order === 'asc' ? '升序' : '降序' }}</button></div>
    <div class="inline search-row"><input v-model="searchText" placeholder="搜索文件名或文档内容" @keyup.enter="search" /><select v-model="searchMode" aria-label="搜索方式"><option value="name">文件名</option><option value="content">混合内容检索</option></select><button class="secondary" @click="search">搜索</button><button v-if="hits.length" class="subtle" @click="hits = []; searchText = ''">清除结果</button></div>
  </section>
  <section v-if="hits.length" class="panel"><h2>搜索结果</h2><div v-for="hit in hits" :key="hit.fileId || hit.id" class="hit"><button class="link" @click="openPreview({ id: hit.fileId || hit.id })">{{ hit.fileName }}</button><p v-if="hit.snippet" class="muted">{{ hit.snippet }}</p><small v-if="hit.version">第 {{ hit.version }} 版</small></div></section>
  <section class="panel">
    <div class="inline breadcrumbs"><button v-for="(step, index) in trail" :key="step.id" class="link" @click="goTo(index)">{{ step.name }}<span v-if="index < trail.length - 1"> / </span></button></div>
    <div v-if="selected.length" class="inline bulk"><span>已选 {{ selected.length }} 项</span><button class="subtle" @click="batch('download')">打包下载</button><button class="subtle" @click="batch('move')">移动</button><button class="subtle" @click="batch('share')">打包分享</button><button class="danger" @click="batch('delete')">删除</button></div>
    <div class="table-wrap"><table><thead><tr><th><input type="checkbox" :checked="files.length > 0 && selected.length === files.length" aria-label="全选" @change="all" /></th><th>名称</th><th>大小</th><th>版本</th><th>操作</th></tr></thead><tbody><tr v-for="file in files" :key="file.id"><td><input type="checkbox" :checked="selected.includes(file.id)" :aria-label="`选择 ${file.fileName}`" @change="toggle(file.id)" /></td><td><button class="file-name" @click="enter(file)"><span class="file-icon">{{ isFolder(file) ? '▣' : '▤' }}</span>{{ file.fileName }}<span v-if="file.starred" title="已收藏">★</span></button><small v-if="file.remark" class="muted">{{ file.remark }}</small></td><td>{{ isFolder(file) ? '文件夹' : bytes(file.fileSize) }}</td><td>{{ isFolder(file) ? '—' : `v${file.version}` }}</td><td class="actions"><button v-if="!isFolder(file)" @click="action('download', file)">下载</button><button v-if="!isFolder(file)" @click="action('versions', file)">版本 / 索引</button><button @click="action('share', file)">分享</button><button @click="action('rename', file)">重命名</button><button @click="action('move', file)">移动</button><button @click="action('star', file)">{{ file.starred ? '取消收藏' : '收藏' }}</button><button @click="action('remark', file)">备注</button><button class="danger-text" @click="action('delete', file)">删除</button></td></tr><tr v-if="!files.length"><td colspan="5" class="empty">暂无文件，上传一份文档开始使用。</td></tr></tbody></table></div>
  </section>
  <section v-if="active && showVersions" class="panel"><div class="inline"><h2>{{ active.fileName }} · 历史版本</h2><button class="subtle" @click="showVersions = false">关闭</button></div><div class="inline index-line"><span>索引状态：{{ indexStatus?.status || '暂无任务' }}<span v-if="indexStatus?.lastError"> · {{ indexStatus.lastError }}</span></span><button v-if="indexStatus?.status === 'FAILED'" class="secondary" @click="retryIndex">重试索引</button><button class="subtle" @click="refreshIndex">刷新</button></div><div v-for="version in versions" :key="version.id" class="version-row"><span>第 {{ version.versionNumber }} 版 · {{ bytes(version.fileSize) }}</span><div class="inline"><button @click="versionAction('preview', version)">预览</button><button @click="versionAction('download', version)">下载</button><button @click="versionAction('rollback', version)">回滚</button><button class="danger-text" @click="versionAction('delete', version)">删除</button></div></div><p v-if="!versions.length" class="muted">暂无历史版本。</p></section>
  <section v-if="shareOpen" class="panel"><div class="inline"><h2>{{ active ? '分享文件' : '打包分享' }}</h2><button class="subtle" @click="shareOpen = false">关闭</button></div><div class="inline wrap"><label>提取码<input v-model="shareForm.password" placeholder="可留空" /></label><label>有效天数<input v-model="shareForm.expireDays" type="number" min="1" placeholder="不限" /></label><label>访问次数<input v-model="shareForm.maxVisits" type="number" min="1" placeholder="不限" /></label><button class="primary" @click="createShare">创建分享</button></div><p v-if="shareUrl"><a :href="shareUrl" target="_blank" rel="noopener">{{ shareUrl }}</a></p></section>
  <section v-if="aiOpen" class="panel ai-panel"><div class="inline"><div><p class="eyebrow">PRIVATE RAG</p><h2>问我的文档</h2></div><button class="subtle" @click="aiOpen = false">关闭</button></div><p class="muted">仅检索当前账号已索引的文档。命中的片段会发送给 DeepSeek 生成回答。</p><div v-for="(turn, index) in turns" :key="index" class="turn"><p class="question">{{ turn.question }}</p><p class="answer">{{ turn.answer || (turn.error ? '' : '正在查找依据…') }}</p><p v-if="turn.error" class="error">{{ turn.error }}</p><div v-if="turn.sources.length" class="sources"><button v-for="source in turn.sources" :key="source.number" class="source" @click="openPreview({ id: source.fileId })">[{{ source.number }}] {{ source.fileName }} · v{{ source.version }}<small>{{ source.snippet }}</small></button></div></div><form class="inline" @submit.prevent="ask"><input v-model="question" placeholder="例如：文档里关于部署步骤是怎么说的？" maxlength="1000" /><button class="primary" :disabled="asking">{{ asking ? '回答中…' : '发送' }}</button></form></section>
  <div v-if="previewUrl" class="modal-backdrop" @click.self="previewUrl = ''"><section class="preview-modal"><div class="inline"><h2>文件预览</h2><button class="subtle" @click="previewUrl = ''">关闭</button></div><iframe :src="previewUrl" title="文件预览"></iframe></section></div>
</template>
