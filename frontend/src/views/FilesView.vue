<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { api, post, stream } from '../api'
import AppDialog from '../components/AppDialog.vue'
import FolderPicker from '../components/FolderPicker.vue'

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
const aiOpen = ref(window.innerWidth > 1100)
const aiFile = ref(null)
const overview = ref({ ready: 0, processing: 0, failed: 0 })
const view = ref('cards')
const dialog = ref('')
const dialogValue = ref('')
const dialogBusy = ref(false)
const moveIds = ref([])
const searchState = ref('idle')
const question = ref('')
const asking = ref(false)
const turns = ref([])
const previewUrl = ref('')
const previewName = ref('')
const previewDownloadUrl = ref('')
const canPreview = ref(false)
const isFolder = file => file?.folder === true
const showVersions = ref(false)
const bytes = value => value == null ? '—' : value < 1024 ? `${value} B` : value < 1048576 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1048576).toFixed(1)} MB`

async function load() {
  try {
    const params = new URLSearchParams({ parentId: parentId.value, category: category.value, sortBy: sortBy.value, order: order.value })
    files.value = await api(`/api/file/list?${params}`) || []
    selected.value = []
    await loadOverview()
  } catch (error) { message.value = error.message }
}
onMounted(load)
async function loadOverview() { try { overview.value = await api('/api/ai/index/overview') } catch { /* file listing still works */ } }
watch([category, sortBy, order], load)
function enter(file) { if (isFolder(file)) { parentId.value = file.id; trail.value.push({ id: file.id, name: file.fileName }); load() } else openPreview(file) }
function goTo(index) { parentId.value = trail.value[index].id; trail.value = trail.value.slice(0, index + 1); load() }
function toggle(id) { selected.value = selected.value.includes(id) ? selected.value.filter(item => item !== id) : [...selected.value, id] }
function all() { selected.value = selected.value.length === files.value.length ? [] : files.value.map(file => file.id) }
function openPreview(file) {
  previewName.value = file.fileName || '来源文件'
  canPreview.value = /\.(pdf|txt|md|png|jpe?g|gif|webp)$/i.test(previewName.value)
  previewDownloadUrl.value = `/api/file/download?fileId=${file.id}`
  previewUrl.value = `/api/file/preview/${file.id}`
}
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
function folder() { dialogValue.value = ''; dialog.value = 'folder' }
async function run(path, fields, refresh = true) {
  try {
    const result = await post(path, fields)
    if (result?.success === false) throw new Error(result.message || '操作失败')
    message.value = typeof result === 'string' ? result : result.message || '操作完成'
    if (refresh) await load()
    return result
  } catch (error) { message.value = error.message; return null }
}
async function action(name, file) {
  active.value = file
  if (name === 'download') { window.location.href = `/api/file/download?fileId=${file.id}`; return }
  if (name === 'versions') { showVersions.value = true; versions.value = await api(`/api/file/versions?fileId=${file.id}`) || []; indexStatus.value = await api(`/api/ai/index/${file.id}`); return }
  if (name === 'share') { shareOpen.value = true; shareUrl.value = ''; return }
  if (name === 'rename' || name === 'remark') { dialogValue.value = name === 'rename' ? file.fileName : file.remark || ''; dialog.value = name; return }
  if (name === 'move') { moveIds.value = [file.id]; dialog.value = 'move'; return }
  if (name === 'ask') { aiFile.value = file; aiOpen.value = true; document.querySelector('.ai-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); return }
  if (name === 'star') await run('/api/file/toggle-star', { fileId: file.id })
  if (name === 'delete') dialog.value = 'delete'
}
async function batch(name) {
  if (!selected.value.length) return
  const ids = selected.value.join(',')
  if (name === 'download') { window.location.href = `/api/file/batch-download?fileIds=${ids}`; return }
  if (name === 'delete') dialog.value = 'batch-delete'
  if (name === 'move') { moveIds.value = [...selected.value]; dialog.value = 'move' }
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
    if (!searchText.value.trim()) { hits.value = []; searchState.value = 'idle'; await load(); return }
    searchState.value = 'loading'
    hits.value = searchMode.value === 'content'
      ? await api(`/api/ai/search?q=${encodeURIComponent(searchText.value)}`)
      : await api(`/api/file/search?keyword=${encodeURIComponent(searchText.value)}&category=${category.value}`)
    if (searchMode.value === 'content') hits.value = [...new Map(hits.value.map(hit => [hit.fileId, hit])).values()]
    searchState.value = hits.value.length ? 'results' : 'empty'
  } catch (error) { message.value = error.message; searchState.value = 'error' }
}
async function ask() {
  const text = question.value.trim()
  if (!text || asking.value) return
  const turn = reactive({ question: text, answer: '', sources: [], error: '' })
  turns.value.push(turn)
  question.value = ''
  asking.value = true
  try {
    await stream('/api/ai/chat', text, (event, data) => {
      if (event === 'source') turn.sources.push(data)
      if (event === 'token') turn.answer += data
      if (event === 'error') turn.error = data
    }, undefined, aiFile.value?.id)
  } catch (error) { turn.error = error.message }
  finally { asking.value = false }
}
async function retryIndex() {
  try { indexStatus.value = await post(`/api/ai/index/${active.value.id}/retry`, {}); message.value = '已重新提交索引任务' }
  catch (error) { message.value = error.message }
}
async function refreshIndex() { indexStatus.value = await api(`/api/ai/index/${active.value.id}`) }
async function versionAction(name, version) {
  if (name === 'preview') { previewName.value = version.fileName || active.value.fileName; canPreview.value = /\.(pdf|txt|md|png|jpe?g|gif|webp)$/i.test(previewName.value); previewDownloadUrl.value = `/api/file/version/download/${version.id}`; previewUrl.value = `/api/file/version/preview/${version.id}`; return }
  if (name === 'download') { window.location.href = `/api/file/version/download/${version.id}`; return }
  if (name === 'rollback' || name === 'delete') { activeVersion.value = version; dialog.value = `version-${name}`; return }
  versions.value = await api(`/api/file/versions?fileId=${active.value.id}`) || []
}
const activeVersion = ref(null)
async function confirmDialog() {
  dialogBusy.value = true
  try {
    const name = dialog.value
    let result
    if (name === 'folder' && dialogValue.value.trim()) result = await run('/api/file/folder/create', { folderName: dialogValue.value.trim(), parentId: parentId.value })
    if (name === 'rename' && dialogValue.value.trim()) result = await run('/api/file/rename', { fileId: active.value.id, newName: dialogValue.value.trim() })
    if (name === 'remark') result = await run('/api/file/update-remark', { fileId: active.value.id, remark: dialogValue.value })
    if (name === 'delete') result = await run('/api/file/delete', { fileId: active.value.id })
    if (name === 'batch-delete') result = await run('/api/file/batch-delete', { fileIds: selected.value.join(',') })
    if (name === 'version-rollback') result = await run('/api/file/rollback', { fileId: active.value.id, version: activeVersion.value.versionNumber })
    if (name === 'version-delete') result = await run('/api/file/version/delete', { fileId: active.value.id, versionId: activeVersion.value.id })
    if (result === null) return
    if (name.startsWith('version-')) versions.value = await api(`/api/file/versions?fileId=${active.value.id}`) || []
    dialog.value = ''
  } finally { dialogBusy.value = false }
}
async function chooseFolder(targetParentId) {
  const ids = moveIds.value
  const result = ids.length === 1
    ? await run('/api/file/move', { fileId: ids[0], targetParentId })
    : await run('/api/file/batch-move', { fileIds: ids.join(','), targetParentId })
  if (result !== null) dialog.value = ''
}
</script>

<template>
  <div class="page-head"><div><p class="eyebrow">MY LITTLE CLOUD</p><h1>我的文件</h1><p class="muted">收好每一份文件，也找到藏在其中的答案。</p></div><button class="primary" @click="aiOpen = !aiOpen">✦ {{ aiOpen ? '收起问答' : '问我的文档' }}</button></div>
  <p v-if="message" class="notice" role="status">{{ message }}</p>
  <div class="file-layout" :class="{ 'ai-hidden': !aiOpen }"><div class="file-main">
  <section class="panel toolbar">
    <div class="inline wrap"><label class="upload primary">{{ busy ? '上传中…' : '上传文件' }}<input type="file" multiple :disabled="busy" @change="upload" /></label><button class="secondary" @click="folder">新建文件夹</button><span class="spacer"></span><select v-model="category" aria-label="文件分类"><option value="">全部类型</option><option value="document">文档</option><option value="image">图片</option><option value="video">视频</option><option value="audio">音频</option><option value="archive">压缩包</option></select><select v-model="sortBy" aria-label="排序"><option value="name">按名称</option><option value="time">按时间</option><option value="size">按大小</option></select><button class="subtle" @click="order = order === 'asc' ? 'desc' : 'asc'">{{ order === 'asc' ? '升序' : '降序' }}</button></div>
    <div class="inline search-row"><input v-model="searchText" placeholder="搜索文件名或文档内容" @keyup.enter="search" /><select v-model="searchMode" aria-label="搜索方式"><option value="name">文件名</option><option value="content">文档内容</option></select><button class="secondary" :disabled="searchState === 'loading'" @click="search">{{ searchState === 'loading' ? '搜索中…' : '搜索' }}</button><button v-if="searchState !== 'idle'" class="subtle" @click="hits = []; searchText = ''; searchState = 'idle'">清除结果</button></div>
  </section>
  <section v-if="searchState !== 'idle'" class="panel"><h2>搜索结果</h2><p v-if="searchState === 'loading'" class="muted">正在查找相关文件…</p><p v-else-if="searchState === 'empty'" class="empty">未找到相关内容。试试文件名、文档中的关键词，或等待索引完成。</p><p v-else-if="searchState === 'error'" class="error">搜索失败，请稍后重试。</p><div v-for="hit in hits" :key="hit.fileId || hit.id" class="hit"><button class="link" @click="openPreview({ id: hit.fileId || hit.id, fileName: hit.fileName })">{{ hit.fileName }}</button><p v-if="hit.snippet" class="muted">{{ hit.snippet }}</p><small v-if="hit.version">第 {{ hit.version }} 版 · 点击文件名打开来源</small></div></section>
  <section class="panel">
    <div class="inline breadcrumbs"><button v-for="(step, index) in trail" :key="step.id" class="link" @click="goTo(index)">{{ step.name }}<span v-if="index < trail.length - 1"> / </span></button><span class="spacer"></span><button class="subtle" :aria-pressed="view === 'cards'" @click="view = 'cards'">▦ 卡片</button><button class="subtle" :aria-pressed="view === 'list'" @click="view = 'list'">☷ 列表</button></div>
    <div v-if="selected.length" class="inline bulk"><span>已选 {{ selected.length }} 项</span><button class="subtle" @click="batch('download')">打包下载</button><button class="subtle" @click="batch('move')">移动</button><button class="subtle" @click="batch('share')">打包分享</button><button class="danger" @click="batch('delete')">删除</button></div>
    <div v-if="view === 'cards'" class="file-cards"><article v-for="file in files" :key="file.id" class="file-card"><div class="inline"><input type="checkbox" :checked="selected.includes(file.id)" :aria-label="`选择 ${file.fileName}`" @change="toggle(file.id)" /><span class="file-icon">{{ isFolder(file) ? '📁' : '▤' }}</span><span class="spacer"></span><span v-if="file.starred">★</span></div><button class="file-name" @click="enter(file)">{{ file.fileName }}</button><p class="muted">{{ isFolder(file) ? '文件夹' : `${bytes(file.fileSize)} · v${file.version}` }}</p><p v-if="file.remark" class="muted">{{ file.remark }}</p><div class="card-actions"><button v-if="!isFolder(file)" @click="action('ask', file)">✦ 问此文件</button><button v-if="!isFolder(file)" @click="action('download', file)">下载</button><button v-if="!isFolder(file)" @click="action('versions', file)">版本 / 索引</button><button @click="action('share', file)">分享</button><button @click="action('move', file)">移动</button><button @click="action('rename', file)">重命名</button><button @click="action('remark', file)">备注</button><button @click="action('star', file)">{{ file.starred ? '取消收藏' : '收藏' }}</button><button class="danger-text" @click="action('delete', file)">删除</button></div></article><p v-if="!files.length" class="empty">这里还空着。上传一份文件，开始整理你的空间。</p></div>
    <div v-else class="table-wrap"><table><thead><tr><th><input type="checkbox" :checked="files.length > 0 && selected.length === files.length" aria-label="全选" @change="all" /></th><th>名称</th><th>大小</th><th>版本</th><th>操作</th></tr></thead><tbody><tr v-for="file in files" :key="file.id"><td><input type="checkbox" :checked="selected.includes(file.id)" :aria-label="`选择 ${file.fileName}`" @change="toggle(file.id)" /></td><td><button class="file-name" @click="enter(file)"><span class="file-icon">{{ isFolder(file) ? '📁' : '▤' }}</span>{{ file.fileName }}<span v-if="file.starred" title="已收藏">★</span></button><small v-if="file.remark" class="muted">{{ file.remark }}</small></td><td>{{ isFolder(file) ? '文件夹' : bytes(file.fileSize) }}</td><td>{{ isFolder(file) ? '—' : `v${file.version}` }}</td><td class="actions"><button v-if="!isFolder(file)" @click="action('ask', file)">问此文件</button><button v-if="!isFolder(file)" @click="action('download', file)">下载</button><button v-if="!isFolder(file)" @click="action('versions', file)">版本 / 索引</button><button @click="action('share', file)">分享</button><button @click="action('rename', file)">重命名</button><button @click="action('move', file)">移动</button><button @click="action('star', file)">{{ file.starred ? '取消收藏' : '收藏' }}</button><button @click="action('remark', file)">备注</button><button class="danger-text" @click="action('delete', file)">删除</button></td></tr><tr v-if="!files.length"><td colspan="5" class="empty">暂无文件，上传一份文档开始使用。</td></tr></tbody></table></div>
  </section>
  <section v-if="active && showVersions" class="panel"><div class="inline"><h2>{{ active.fileName }} · 历史版本</h2><button class="subtle" @click="showVersions = false">关闭</button></div><div class="inline index-line"><span>索引状态：{{ indexStatus?.status || '暂无任务' }}<span v-if="indexStatus?.lastError"> · {{ indexStatus.lastError }}</span></span><button v-if="indexStatus?.status === 'FAILED'" class="secondary" @click="retryIndex">重试索引</button><button class="subtle" @click="refreshIndex">刷新</button></div><div v-for="version in versions" :key="version.id" class="version-row"><span>第 {{ version.versionNumber }} 版 · {{ bytes(version.fileSize) }}</span><div class="inline"><button @click="versionAction('preview', version)">预览</button><button @click="versionAction('download', version)">下载</button><button @click="versionAction('rollback', version)">回滚</button><button class="danger-text" @click="versionAction('delete', version)">删除</button></div></div><p v-if="!versions.length" class="muted">暂无历史版本。</p></section>
  </div><aside v-if="aiOpen" class="panel ai-panel"><div class="inline"><div><p class="eyebrow">ASK YOUR DOCUMENTS</p><h2>问我的文档</h2></div><button class="subtle" @click="aiOpen = false">关闭</button></div><p class="muted">默认询问当前账号全部已索引文档，无需先选文件。支持 PDF、Word、TXT、MD、Excel、PPT 和 CSV。</p><p class="index-summary">已就绪 {{ overview.ready }} · 处理中 {{ overview.processing }} · 失败 {{ overview.failed }} <button class="link" @click="loadOverview">刷新</button></p><div v-if="aiFile" class="scope-chip">仅问：{{ aiFile.fileName }} <button class="link" @click="aiFile = null">改为全部文档</button></div><p v-else class="muted">试试：“项目里提到的部署步骤是什么？”</p><div class="conversation"><div v-for="(turn, index) in turns" :key="index" class="turn"><p class="question">{{ turn.question }}</p><p class="answer">{{ turn.answer || (turn.error ? '' : '正在查找依据…') }}</p><p v-if="turn.error" class="error">{{ turn.error }}</p><div v-if="turn.sources.length" class="sources"><button v-for="source in turn.sources" :key="source.number" class="source" @click="openPreview({ id: source.fileId, fileName: source.fileName })">[{{ source.number }}] {{ source.fileName }} · v{{ source.version }}<small>{{ source.snippet }}</small></button></div></div></div><form class="ai-form" @submit.prevent="ask"><textarea v-model="question" placeholder="直接输入问题，按发送开始…" maxlength="1000" rows="3"></textarea><button class="primary" :disabled="asking || !question.trim()">{{ asking ? '回答中…' : '发送问题 →' }}</button></form><p class="muted">点击回答下方的来源，可打开原文件。浏览器无法预览的格式请下载查看。</p></aside></div>
  <AppDialog v-if="shareOpen" :title="active ? '分享文件' : '打包分享'" confirm-text="创建分享" @close="shareOpen = false" @confirm="createShare"><label>提取码<input v-model="shareForm.password" placeholder="可留空" /></label><label>有效天数<input v-model="shareForm.expireDays" type="number" min="1" placeholder="不限" /></label><label>访问次数<input v-model="shareForm.maxVisits" type="number" min="1" placeholder="不限" /></label><p v-if="shareUrl"><a :href="shareUrl" target="_blank" rel="noopener">{{ shareUrl }}</a></p></AppDialog>
  <FolderPicker v-if="dialog === 'move'" :exclude-ids="moveIds" @close="dialog = ''" @choose="chooseFolder" />
  <AppDialog v-else-if="dialog" :title="({ folder: '新建文件夹', rename: '重命名', remark: '编辑备注', delete: '移入回收站', 'batch-delete': '批量移入回收站', 'version-rollback': '回滚版本', 'version-delete': '删除历史版本' })[dialog]" :busy="dialogBusy" @close="dialog = ''" @confirm="confirmDialog"><label v-if="['folder', 'rename', 'remark'].includes(dialog)">{{ dialog === 'folder' ? '文件夹名称' : dialog === 'rename' ? '新名称' : '备注' }}<input v-model="dialogValue" :required="dialog !== 'remark'" autofocus /></label><p v-else class="muted">{{ dialog === 'version-delete' ? '此历史版本删除后无法恢复。' : dialog === 'version-rollback' ? `确定回滚到第 ${activeVersion?.versionNumber} 版？` : '确定将所选项目移入回收站？' }}</p></AppDialog>
  <div v-if="previewUrl" class="modal-backdrop" @click.self="previewUrl = ''"><section class="preview-modal"><div class="inline"><h2>{{ previewName }}</h2><a :href="previewDownloadUrl" class="secondary">下载文件</a><button class="subtle" @click="previewUrl = ''">关闭</button></div><iframe v-if="canPreview" :src="previewUrl" title="文件预览"></iframe><p v-else class="empty">浏览器暂不支持预览这种格式，请下载后查看。</p></section></div>
</template>
