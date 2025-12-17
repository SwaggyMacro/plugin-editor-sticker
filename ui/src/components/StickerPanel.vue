<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import type { StickerGroup, Sticker } from '@/types/sticker'

const props = defineProps<{
  visible: boolean
  stickerGroups: StickerGroup[]
  position?: { top: number; left: number }
}>()

const emit = defineEmits<{
  (e: 'select', sticker: Sticker): void
  (e: 'close'): void
}>()

const activeGroupIndex = ref(0)
const searchKeyword = ref('')

// 预览相关
const previewSticker = ref<Sticker | null>(null)
const previewPosition = ref({ x: 0, y: 0 })

const currentGroup = computed(() => props.stickerGroups[activeGroupIndex.value])

const filteredStickers = computed(() => {
  if (!currentGroup.value) return []
  if (!searchKeyword.value) return currentGroup.value.stickers
  const keyword = searchKeyword.value.toLowerCase()
  return currentGroup.value.stickers.filter(
    (s) =>
      s.name.toLowerCase().includes(keyword) ||
      s.alt?.toLowerCase().includes(keyword)
  )
})

const handleSelect = (sticker: Sticker) => {
  emit('select', sticker)
}

const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (!target.closest('.sticker-panel') && !target.closest('.sticker-trigger')) {
    emit('close')
  }
}

// 鼠标悬浮预览
const handleMouseEnter = (sticker: Sticker, event: MouseEvent) => {
  if (!isImageSticker(sticker)) return
  previewSticker.value = sticker
  updatePreviewPosition(event)
}

const handleMouseMove = (event: MouseEvent) => {
  if (previewSticker.value) {
    updatePreviewPosition(event)
  }
}

const handleMouseLeave = () => {
  previewSticker.value = null
}

const updatePreviewPosition = (event: MouseEvent) => {
  const offset = 15
  previewPosition.value = {
    x: event.clientX - offset,
    y: event.clientY - offset
  }
}

// 获取预览图 URL（优先使用 originUrl）
const getPreviewUrl = (sticker: Sticker) => {
  return sticker.originUrl || sticker.url
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

watch(
  () => props.visible,
  (val) => {
    if (!val) {
      searchKeyword.value = ''
      previewSticker.value = null
    }
  }
)

// 判断是否为图片表情
const isImageSticker = (sticker: Sticker) => {
  return sticker.url && (sticker.url.startsWith('http') || sticker.url.startsWith('/'))
}
</script>

<template>
  <Transition name="fade">
    <div
      v-if="visible"
      class="sticker-panel"
      :style="position ? { top: position.top + 'px', left: position.left + 'px' } : undefined"
      @click.stop
    >
      <div class="sticker-panel__tabs">
        <button
          v-for="(group, index) in stickerGroups"
          :key="group.name"
          :class="['sticker-panel__tab', { active: activeGroupIndex === index }]"
          :title="group.name"
          @click="activeGroupIndex = index"
        >
          {{ group.name }}
        </button>
      </div>
      <div class="sticker-panel__content">
        <div class="sticker-panel__grid" :class="{ 'text-mode': !currentGroup?.stickers[0]?.url }">
          <button
            v-for="sticker in filteredStickers"
            :key="sticker.name"
            class="sticker-panel__item"
            :class="{ 'text-sticker': !isImageSticker(sticker) }"
            :title="sticker.alt || sticker.name"
            @click="handleSelect(sticker)"
            @mouseenter="handleMouseEnter(sticker, $event)"
            @mousemove="handleMouseMove"
            @mouseleave="handleMouseLeave"
          >
            <img
              v-if="isImageSticker(sticker)"
              :src="sticker.url"
              :alt="sticker.alt || sticker.name"
              loading="lazy"
            />
            <span v-else class="sticker-text">{{ sticker.alt || sticker.name }}</span>
          </button>
        </div>
        <div v-if="filteredStickers.length === 0" class="sticker-panel__empty">
          没有找到表情
        </div>
      </div>
    </div>
  </Transition>
  
  <!-- 预览窗 -->
  <Transition name="preview-fade">
    <div
      v-if="previewSticker"
      class="sticker-preview"
      :style="{ left: previewPosition.x + 'px', top: previewPosition.y + 'px' }"
    >
      <img :src="getPreviewUrl(previewSticker)" :alt="previewSticker.alt || previewSticker.name" />
      <div class="sticker-preview__name">{{ previewSticker.alt || previewSticker.name }}</div>
    </div>
  </Transition>
</template>

<style lang="scss" scoped>
.sticker-panel {
  position: fixed;
  width: 340px;
  max-height: 380px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 9999;
  display: flex;
  flex-direction: column;

  &__tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    padding: 6px 8px;
    border-bottom: 1px solid #e5e7eb;
    max-height: 180px;
    overflow-y: auto;

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-thumb {
      background: #d1d5db;
      border-radius: 2px;
    }
  }

  &__tab {
    flex-shrink: 0;
    min-width: 32px;
    height: 32px;
    padding: 4px 8px;
    border: none;
    background: transparent;
    border-radius: 4px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: #6b7280;

    &:hover {
      background: #f3f4f6;
    }

    &.active {
      background: #e5e7eb;
      color: #374151;
    }
  }

  &__tab-icon {
    width: 24px;
    height: 24px;
    object-fit: contain;
  }

  &__content {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
    min-height: 150px;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(6, 1fr);
    gap: 4px;

    &.text-mode {
      grid-template-columns: repeat(3, 1fr);
    }
  }

  &__item {
    width: 100%;
    aspect-ratio: 1;
    padding: 4px;
    border: none;
    background: transparent;
    border-radius: 4px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 0.15s ease;

    &:hover {
      background: #f3f4f6;
    }

    img {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
    }

    &.text-sticker {
      aspect-ratio: auto;
      padding: 6px 8px;
      font-size: 14px;

      .sticker-text {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
  }

  &__empty {
    text-align: center;
    color: #9ca3af;
    padding: 20px;
    font-size: 13px;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

</style>

<style lang="scss">
// 预览窗样式 (不使用 scoped，因为 Teleport 到 body)
.sticker-preview {
  position: fixed;
  z-index: 10000;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  padding: 8px;
  pointer-events: none;
  max-width: 200px;
  transform: translate(-100%, -100%);

  img {
    max-width: 180px;
    max-height: 180px;
    display: block;
    object-fit: contain;
  }

  .sticker-preview__name {
    margin-top: 6px;
    font-size: 12px;
    color: #6b7280;
    text-align: center;
    word-break: break-all;
  }
}

.preview-fade-enter-active,
.preview-fade-leave-active {
  transition: opacity 0.1s ease;
}

.preview-fade-enter-from,
.preview-fade-leave-to {
  opacity: 0;
}
</style>
