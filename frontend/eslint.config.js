import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'

export default [
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.{js,vue}'],
    languageOptions: { globals: { window: 'readonly', document: 'readonly', fetch: 'readonly', URLSearchParams: 'readonly', FormData: 'readonly', TextDecoder: 'readonly', Uint8Array: 'readonly', console: 'readonly', globalThis: 'readonly', ReadableStream: 'readonly', Response: 'readonly', TextEncoder: 'readonly' } },
    rules: { 'vue/multi-word-component-names': 'off', 'vue/html-self-closing': 'off' }
  }
]
