<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api, stream } from '../api'
import MarkdownText from '../components/MarkdownText.vue'

const route = useRoute()
const overview = ref({ ready: 0, processing: 0, failed: 0 })
const readyFiles = ref([])
const scope = ref('all')
const chosenFiles = ref([])
const singleFile = ref(null)
const question = ref('')
const asking = ref(false)
const turns = ref([])
const message = ref('')
const preview = ref(null)
const canPreview = name => /\.(pdf|txt|md|png|jpe?g|gif|webp)$/i.test(name || '')

async function load() {
  try {
    overview.value = await api('/api/ai/index/overview')
    readyFiles.value = await api('/api/ai/index/ready-files')
    chosenFiles.value = chosenFiles.value.filter(id => readyFiles.value.some(file => file.id === id))
    const id = Number(route.query.fileId)
    singleFile.value = id ? readyFiles.value.find(file => file.id === id) || { id, fileName: '指定文件（尚未就绪）', ready: false } : null
    if (id && singleFile.value.ready === false) message.value = '指定文件还没有完成索引，或不是支持的文档格式，请稍后刷新。'
    if (singleFile.value) scope.value = 'one'
  } catch (error) { message.value = error.message }
}
onMounted(load)
watch(() => route.query.fileId, load)

async function ask() {
  const text = question.value.trim()
  if (!text || asking.value) return
  const ids = scope.value === 'one' ? [singleFile.value?.id] : scope.value === 'selected' ? [...chosenFiles.value] : []
  if (ids.some(id => !id) || (scope.value === 'one' && singleFile.value?.ready === false) || (scope.value === 'selected' && !ids.length)) { message.value = '请先选择已就绪文档'; return }
  const label = scope.value === 'one' ? singleFile.value.fileName : scope.value === 'selected' ? `所选 ${ids.length} 份文档` : '全部已就绪文档'
  const turn = reactive({ question: text, label, answer: '', sources: [], error: '' })
  turns.value.push(turn)
  question.value = ''
  message.value = ''
  asking.value = true
  try {
    await stream('/api/ai/chat', text, (event, data) => {
      if (event === 'source') turn.sources.push(data)
      if (event === 'token') turn.answer += data
      if (event === 'error') turn.error = data
    }, ids)
  } catch (error) { turn.error = error.message }
  finally { if (!turn.answer && !turn.error) turn.error = '没有收到回答，请重试'; asking.value = false }
}

function openSource(source) { preview.value = source }
</script>

<template>
  <div class="page-head"><div><p class="eyebrow">ASK YOUR DOCUMENTS</p><h1>问我的文档</h1><p class="muted">选择知识范围，再问一个具体问题；回答下方可以打开来源。</p></div><RouterLink class="secondary" to="/files">返回文件</RouterLink></div>
  <p v-if="message" class="notice" role="status">{{ message }}</p>
  <div class="ai-workspace"><section class="panel ai-settings"><h2>知识范围</h2><p class="index-summary">已就绪 {{ overview.ready }} · 处理中 {{ overview.processing }} · 失败 {{ overview.failed }} <button class="link" @click="load">刷新</button></p><div class="ai-scope"><label><input v-model="scope" type="radio" value="all" /> 全部已就绪文档</label><label><input v-model="scope" type="radio" value="selected" /> 指定几份文档</label><label v-if="singleFile"><input v-model="scope" type="radio" value="one" /> {{ singleFile.fileName }}</label></div><div v-if="scope === 'selected'" class="ai-file-picker"><p class="muted">选择 1–20 份文档，可跨文件夹。</p><label v-for="file in readyFiles" :key="file.id"><input v-model="chosenFiles" type="checkbox" :value="file.id" :disabled="chosenFiles.length >= 20 && !chosenFiles.includes(file.id)" /> {{ file.fileName }}</label><p v-if="!readyFiles.length" class="muted">暂无已就绪文档。</p></div><p class="muted">支持 PDF、Word、TXT、MD、Excel、PPT 和 CSV。概括性问题也可以直接问“这几份文档讲了什么？”</p></section>
    <section class="panel ai-answer-panel"><div class="inline"><h2>知识库问答</h2><span class="spacer"></span><span class="muted">回答仅依据选定文档</span></div><div class="conversation"><p v-if="!turns.length" class="empty">在下方输入问题，回答和来源会显示在这里。</p><div v-for="(turn, index) in turns" :key="index" class="turn"><small class="muted">{{ turn.label }}</small><p class="question">{{ turn.question }}</p><MarkdownText v-if="turn.answer" class="answer" :text="turn.answer" /><p v-else-if="!turn.error" class="muted">正在查找依据…</p><p v-if="turn.error" class="error">{{ turn.error }}</p><div v-if="turn.sources.length" class="sources"><button v-for="source in turn.sources" :key="source.number" class="source" @click="openSource(source)">[{{ source.number }}] {{ source.fileName }} · v{{ source.version }}<small>{{ source.snippet }}</small></button></div></div></div><form class="ai-form" @submit.prevent="ask"><textarea v-model="question" placeholder="输入你想从文档中了解的问题…" maxlength="1000" rows="3"></textarea><button class="primary" :disabled="asking || !question.trim() || (scope === 'one' && singleFile?.ready === false) || (scope === 'selected' && !chosenFiles.length)">{{ asking ? '回答中…' : '发送问题 →' }}</button></form></section></div>
  <div v-if="preview" class="modal-backdrop" @click.self="preview = null"><section class="preview-modal"><div class="inline"><h2>{{ preview.fileName }}</h2><a class="secondary" :href="`/api/file/download?fileId=${preview.fileId}`">下载文件</a><button class="subtle" @click="preview = null">关闭</button></div><iframe v-if="canPreview(preview.fileName)" :src="`/api/file/preview/${preview.fileId}`" title="来源文件预览"></iframe><p v-else class="empty">浏览器暂不支持预览这种格式，请下载后查看。</p></section></div>
</template>
