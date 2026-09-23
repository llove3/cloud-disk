<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'

const props = defineProps({ text: { type: String, default: '' } })
const markdown = new MarkdownIt({ html: false, linkify: false, breaks: true })
markdown.disable(['image', 'link', 'autolink'])
const rendered = computed(() => markdown.render(props.text
  .replace(/^(#{1,6})(?=\S)/gm, '$1 ')
  .replace(/^([ \t]*[-*+]|[ \t]*\d+[.)])(?=\S)/gm, '$1 ')))
</script>

<template>
  <div class="markdown-text" v-html="rendered"></div>
</template>
