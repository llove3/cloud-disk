<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

defineProps({ title: { type: String, required: true }, confirmText: { type: String, default: '确定' }, busy: Boolean })
const emit = defineEmits(['close', 'confirm'])
const dialog = ref(null)
function keydown(event) { if (event.key === 'Escape') emit('close') }
onMounted(() => { document.addEventListener('keydown', keydown); dialog.value?.focus() })
onUnmounted(() => document.removeEventListener('keydown', keydown))
</script>

<template>
  <div class="modal-backdrop" @click.self="emit('close')">
    <section ref="dialog" class="app-dialog" role="dialog" aria-modal="true" :aria-label="title" tabindex="-1">
      <div class="inline dialog-head"><h2>{{ title }}</h2><button class="subtle" type="button" aria-label="关闭" @click="emit('close')">✕</button></div>
      <form class="stack" @submit.prevent="emit('confirm')"><slot /><div class="inline dialog-actions"><button type="button" class="secondary" @click="emit('close')">取消</button><button class="primary" :disabled="busy">{{ busy ? '处理中…' : confirmText }}</button></div></form>
    </section>
  </div>
</template>
