<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useStickers } from '@/composables/useStickers'
import type { Sticker } from '@/types/sticker'
import StickerPanel from './StickerPanel.vue'

const { stickerGroups, loadStickers, pluginConfig, loadPluginConfig } = useStickers()
const showPanel = ref(false)
const isEnabled = ref(true)
const isVditorPage = ref(false)
const triggerRef = ref<HTMLButtonElement | null>(null)
const panelPosition = ref({ top: 0, left: 0 })

// 监听 Vditor 编辑器的出现
let observer: MutationObserver | null = null

const checkVditorPage = () => {
  const vditor = document.querySelector('#plugin-vditor-mde')
  isVditorPage.value = !!vditor
}

const updatePanelPosition = () => {
  if (!triggerRef.value) return
  
  const rect = triggerRef.value.getBoundingClientRect()
  const panelWidth = 340
  const panelHeight = 380
  
  // 面板显示在按钮左侧
  let left = rect.left - panelWidth - 8
  // 垂直居中对齐按钮
  let top = rect.top + rect.height / 2 - panelHeight / 2
  
  // 如果左边空间不足，显示在按钮右侧
  if (left < 16) {
    left = rect.right + 8
  }
  
  // 如果上方超出
  if (top < 16) {
    top = 16
  }
  
  // 如果下方超出
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
  
  // 尝试多种方式获取 Vditor 实例
  // 方式1: 通过 DOM 元素的 Vditor 属性
  const vditorEl = document.querySelector('#vditor')
  if (vditorEl) {
    // Vditor 实例可能挂载在不同的属性上
    const vditor = (vditorEl as any).vditor || (vditorEl as any).__vditor
    if (vditor && typeof vditor.insertValue === 'function') {
      vditor.insertValue(content)
      showPanel.value = false
      return
    }
  }
  
  // 方式2: 直接操作编辑区域
  const editArea = document.querySelector('#plugin-vditor-mde .vditor-ir .vditor-reset') ||
                   document.querySelector('#plugin-vditor-mde .vditor-wysiwyg .vditor-reset') ||
                   document.querySelector('#plugin-vditor-mde .vditor-sv .vditor-reset')
  if (editArea) {
    // 使用 execCommand 插入文本
    const selection = window.getSelection()
    if (selection && selection.rangeCount > 0) {
      const range = selection.getRangeAt(0)
      const textNode = document.createTextNode(content)
      range.insertNode(textNode)
      range.setStartAfter(textNode)
      range.collapse(true)
      selection.removeAllRanges()
      selection.addRange(range)
      // 触发 input 事件
      editArea.dispatchEvent(new Event('input', { bubbles: true }))
      showPanel.value = false
      return
    }
  }
  
  console.warn('[editor-sticker] Cannot insert to Vditor')
  showPanel.value = false
}

const closePanel = () => {
  showPanel.value = false
}

const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (!target.closest('.sticker-panel') && !target.closest('.vditor-floating-btn')) {
    showPanel.value = false
  }
}

onMounted(async () => {
  await loadPluginConfig()
  isEnabled.value = pluginConfig.value.enableVditorEditor !== false
  
  if (!isEnabled.value) return
  
  await loadStickers()
  
  // 检查是否在 Vditor 页面
  checkVditorPage()
  
  // 监听 DOM 变化
  observer = new MutationObserver(() => {
    checkVditorPage()
  })
  
  observer.observe(document.body, {
    childList: true,
    subtree: true
  })
  
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  if (observer) {
    observer.disconnect()
  }
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <!-- 右下角悬浮按钮 -->
  <button
    v-if="isEnabled && isVditorPage"
    ref="triggerRef"
    class="vditor-floating-btn"
    :class="{ active: showPanel }"
    title="插入表情"
    @click.stop="togglePanel"
  >
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">
      <path fill="currentColor" d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm0 18c-4.411 0-8-3.589-8-8s3.589-8 8-8 8 3.589 8 8-3.589 8-8 8zm3.5-9a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zm-7 0a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zm3.5 6.5c2.33 0 4.31-1.46 5.11-3.5H6.89c.8 2.04 2.78 3.5 5.11 3.5z"/>
    </svg>
  </button>
  
  <!-- 表情面板 -->
  <StickerPanel
    v-if="isEnabled && isVditorPage"
    :visible="showPanel"
    :sticker-groups="stickerGroups"
    :position="panelPosition"
    @select="handleSelect"
    @close="closePanel"
  />
</template>

<style scoped>
.vditor-floating-btn {
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

.vditor-floating-btn:hover {
  background: #f3f4f6;
  transform: scale(1.05);
}

.vditor-floating-btn.active {
  background: #e5e7eb;
  color: #3b82f6;
}

</style>
