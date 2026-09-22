<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'
import AppDialog from './AppDialog.vue'

const props = defineProps({ excludeIds: { type: Array, default: () => [] } })
const emit = defineEmits(['close', 'choose'])
const trail = ref([{ id: 0, name: '全部文件' }])
const folders = ref([])
const loading = ref(false)
const error = ref('')
async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ parentId: trail.value.at(-1).id })
    folders.value = (await api(`/api/file/list?${params}`) || []).filter(item => item.folder && !props.excludeIds.includes(item.id))
  } catch (cause) { error.value = cause.message }
  finally { loading.value = false }
}
function enter(folder) { trail.value.push({ id: folder.id, name: folder.fileName }); load() }
function back(index) { trail.value = trail.value.slice(0, index + 1); load() }
onMounted(load)
</script>

<template>
  <AppDialog title="选择目标文件夹" confirm-text="移动到此处" :busy="loading" @close="emit('close')" @confirm="emit('choose', trail.at(-1).id)">
    <p class="muted">逐级进入文件夹，确认后移动到当前所在目录。</p>
    <div class="inline breadcrumbs"><button v-for="(step, index) in trail" :key="step.id" type="button" class="link" @click="back(index)">{{ step.name }}{{ index < trail.length - 1 ? ' /' : '' }}</button></div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="folder-list"><button v-for="folder in folders" :key="folder.id" type="button" @click="enter(folder)">📁 {{ folder.fileName }} <span>进入 ›</span></button><p v-if="!loading && !folders.length" class="muted">此处没有子文件夹，可以移动到这里。</p></div>
  </AppDialog>
</template>
