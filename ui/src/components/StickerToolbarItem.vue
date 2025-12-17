<script setup lang="ts">
import { ref, onMounted, nextTick, watch } from 'vue'
import { useStickers } from '@/composables/useStickers'
import type { Sticker } from '@/types/sticker'
import RiEmotionLine from '~icons/ri/emotion-line'
import StickerPanel from './StickerPanel.vue'

const props = defineProps<{
  editor?: any
  editorType?: 'default' | 'vditor'
}>()

const emit = defineEmits<{
  (e: 'insert', content: string): void
}>()

const { stickerGroups, loadStickers, pluginConfig, loadPluginConfig } = useStickers()
const showPanel = ref(false)
const triggerRef = ref<HTMLButtonElement | null>(null)
const panelPosition = ref({ top: 0, left: 0 })
const isEnabled = ref(true)

onMounted(async () => {
  await loadPluginConfig()
  // 检查是否启用
  if (props.editorType === 'vditor') {
    isEnabled.value = pluginConfig.value.enableVditorEditor !== false
  } else {
    isEnabled.value = pluginConfig.value.enableDefaultEditor !== false
  }
  if (isEnabled.value) {
    loadStickers()
  }
})

const updatePanelPosition = () => {
  if (!triggerRef.value) return

  const rect = triggerRef.value.getBoundingClientRect()
  const panelWidth = 340
  const panelHeight = 380

  // 计算位置，优先向下弹出
  let top = rect.bottom + 8
  let left = rect.left

  // 如果右边超出视口，向左调整
  if (left + panelWidth > window.innerWidth) {
    left = window.innerWidth - panelWidth - 16
  }

  // 如果左边超出视口
  if (left < 16) {
    left = 16
  }

  // 如果下方空间不足，向上弹出
  if (top + panelHeight > window.innerHeight) {
    top = rect.top - panelHeight - 8
  }

  // 如果上方也不够，就固定在可视区域内
  if (top < 16) {
    top = 16
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

watch(showPanel, (val) => {
  if (val) {
    window.addEventListener('resize', updatePanelPosition)
    window.addEventListener('scroll', updatePanelPosition, true)
  } else {
    window.removeEventListener('resize', updatePanelPosition)
    window.removeEventListener('scroll', updatePanelPosition, true)
  }
})

const handleSelect = (sticker: Sticker) => {
  // 使用短代码格式插入，如 :alu_暗地观察:
  const content = sticker.shortcode || sticker.alt || sticker.name

  emit('insert', content)

  // 如果有编辑器实例，直接插入
  if (props.editor) {
    insertToEditor(content)
  }

  showPanel.value = false
}

const insertToEditor = (content: string) => {
  if (!props.editor) return

  if (props.editorType === 'vditor') {
    // Vditor 编辑器
    if (typeof props.editor.insertValue === 'function') {
      props.editor.insertValue(content)
    }
  } else {
    // 默认编辑器 (TipTap/ProseMirror)
    try {
      props.editor.chain().focus().insertContent(content).run()
    } catch {
      // 降级方案：直接插入文本
      const { state, dispatch } = props.editor.view
      const { from, to } = state.selection
      const tr = state.tr.insertText(content, from, to)
      dispatch(tr)
    }
  }
}

const closePanel = () => {
  showPanel.value = false
}
</script>

<template>
  <div v-if="isEnabled" class="sticker-toolbar-item">
    <button
      ref="triggerRef"
      class="sticker-trigger"
      :class="{ active: showPanel }"
      title="插入表情"
      @click.stop="togglePanel"
    >
      <RiEmotionLine />
    </button>
    <StickerPanel
      :visible="showPanel"
      :sticker-groups="stickerGroups"
      :position="panelPosition"
      @select="handleSelect"
      @close="closePanel"
    />
  </div>
</template>

<style lang="scss" scoped>
.sticker-toolbar-item {
  position: relative;
  display: inline-flex;
}

.sticker-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  color: #374151;
  transition: all 0.15s ease;

  &:hover {
    background: #f3f4f6;
  }

  &.active {
    background: #e5e7eb;
    color: #3b82f6;
  }

  svg {
    width: 18px;
    height: 18px;
  }
}
</style>
