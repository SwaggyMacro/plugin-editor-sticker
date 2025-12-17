<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useStickers } from '@/composables/useStickers'
import type { Sticker } from '@/types/sticker'
import StickerPanel from './StickerPanel.vue'

const { stickerGroups, loadStickers, pluginConfig, loadPluginConfig } = useStickers()
const showPanel = ref(false)
const isEnabled = ref(true)
const isWillowPage = ref(false)
const triggerRef = ref<HTMLButtonElement | null>(null)
const panelPosition = ref({ top: 0, left: 0 })

let observer: MutationObserver | null = null

// 编辑器页面路由匹配
const isEditorRoute = () => {
  const path = window.location.pathname
  return path.includes('/posts/editor') || 
         path.includes('/pages/editor') || 
         path.includes('/singlepages/editor')
}

const checkWillowPage = () => {
  // 先检查路由，再检查编辑器元素
  if (!isEditorRoute()) {
    isWillowPage.value = false
    return
  }
  // Willow MDE 编辑器的容器选择器
  const willow = document.querySelector('.willow-mde') || 
                 document.querySelector('[class*="willow"]') ||
                 document.querySelector('.cm-editor')
  isWillowPage.value = !!willow
}

const updatePanelPosition = () => {
  if (!triggerRef.value) return
  
  const rect = triggerRef.value.getBoundingClientRect()
  const panelWidth = 340
  const panelHeight = 380
  
  let left = rect.left - panelWidth - 8
  let top = rect.top + rect.height / 2 - panelHeight / 2
  
  if (left < 16) {
    left = rect.right + 8
  }
  
  if (top < 16) {
    top = 16
  }
  
  if (top + panelHeight > window.innerHeight - 16) {
    top = window.innerHeight - panelHeight - 16
  }
  
  panelPosition.value = { top, left }
}

const togglePanel = async () => {
  showPanel.value = !showPanel.value
  if (showPanel.value) {
    await nextTick()
    updatePanelPosition()
  }
}

const handleSelect = (sticker: Sticker) => {
  const content = sticker.shortcode || sticker.alt || sticker.name
  
  // Willow 使用 CodeMirror 6
  // 方式1: 通过 cm-content 找到 CodeMirror 编辑器
  const cmContent = document.querySelector('.cm-content') as HTMLElement
  if (cmContent) {
    // CodeMirror 6 的 view 实例通常挂载在 cm-editor 元素上
    const cmEditor = document.querySelector('.cm-editor') as any
    if (cmEditor?.cmView?.view) {
      const view = cmEditor.cmView.view
      const { state } = view
      const { from, to } = state.selection.main
      view.dispatch({
        changes: { from, to, insert: content },
        selection: { anchor: from + content.length }
      })
      view.focus()
      showPanel.value = false
      return
    }
  }
  
  // 方式2: 直接操作 contenteditable
  const editArea = document.querySelector('.cm-content[contenteditable="true"]')
  if (editArea) {
    const selection = window.getSelection()
    if (selection && selection.rangeCount > 0) {
      const range = selection.getRangeAt(0)
      const textNode = document.createTextNode(content)
      range.deleteContents()
      range.insertNode(textNode)
      range.setStartAfter(textNode)
      range.collapse(true)
      selection.removeAllRanges()
      selection.addRange(range)
      editArea.dispatchEvent(new Event('input', { bubbles: true }))
      showPanel.value = false
      return
    }
  }
  
  // 方式3: 通过 textarea (如果有)
  const textarea = document.querySelector('.willow-mde textarea') as HTMLTextAreaElement
  if (textarea) {
    const start = textarea.selectionStart
    const end = textarea.selectionEnd
    const value = textarea.value
    textarea.value = value.substring(0, start) + content + value.substring(end)
    textarea.selectionStart = textarea.selectionEnd = start + content.length
    textarea.dispatchEvent(new Event('input', { bubbles: true }))
    textarea.focus()
    showPanel.value = false
    return
  }
  
  console.warn('[editor-sticker] Cannot insert to Willow MDE')
  showPanel.value = false
}

const closePanel = () => {
  showPanel.value = false
}

const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (!target.closest('.sticker-panel') && !target.closest('.willow-floating-btn')) {
    showPanel.value = false
  }
}

onMounted(async () => {
  await loadPluginConfig()
  isEnabled.value = pluginConfig.value.enableWillowEditor !== false
  
  if (!isEnabled.value) return
  
  await loadStickers()
  
  checkWillowPage()
  
  observer = new MutationObserver(() => {
    checkWillowPage()
  })
  
  observer.observe(document.body, {
    childList: true,
    subtree: true
  })
  
  // 监听路由变化
  window.addEventListener('popstate', checkWillowPage)
  window.addEventListener('hashchange', checkWillowPage)
  
  const originalPushState = history.pushState
  const originalReplaceState = history.replaceState
  history.pushState = function(...args) {
    originalPushState.apply(this, args)
    setTimeout(checkWillowPage, 50)
  }
  history.replaceState = function(...args) {
    originalReplaceState.apply(this, args)
    setTimeout(checkWillowPage, 50)
  }
  
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  if (observer) {
    observer.disconnect()
  }
  window.removeEventListener('popstate', checkWillowPage)
  window.removeEventListener('hashchange', checkWillowPage)
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <button
    v-if="isEnabled && isWillowPage"
    ref="triggerRef"
    class="willow-floating-btn"
    :class="{ active: showPanel }"
    title="插入表情"
    @click.stop="togglePanel"
  >
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
      <path fill="currentColor" d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm0 18c-4.411 0-8-3.589-8-8s3.589-8 8-8 8 3.589 8 8-3.589 8-8 8zm3.5-9a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zm-7 0a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zm3.5 6.5c2.33 0 4.31-1.46 5.11-3.5H6.89c.8 2.04 2.78 3.5 5.11 3.5z"/>
    </svg>
  </button>
  
  <StickerPanel
    v-if="isEnabled && isWillowPage"
    :visible="showPanel"
    :sticker-groups="stickerGroups"
    :position="panelPosition"
    @select="handleSelect"
    @close="closePanel"
  />
</template>

<style scoped>
.willow-floating-btn {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #fff;
  border: 1px solid #e5e7eb;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #374151;
  transition: all 0.2s ease;
  z-index: 9998;
}

.willow-floating-btn:hover {
  background: #f3f4f6;
  transform: scale(1.05);
}

.willow-floating-btn.active {
  background: #e5e7eb;
  color: #3b82f6;
}
</style>
