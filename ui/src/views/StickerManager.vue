<script setup lang="ts">
import { ref, onMounted, computed, nextTick } from 'vue'
import axios from 'axios'
import { VButton, VCard, VSpace, VModal, Toast, Dialog } from '@halo-dev/components'
import type { AttachmentLike } from '@halo-dev/console-shared'
import IconAddCircle from '~icons/ri/add-circle-line'
import IconDeleteBin from '~icons/ri/delete-bin-line'
import IconEdit from '~icons/ri/edit-line'

interface StickerItem {
  icon: string
  text: string
}

interface StickerGroup {
  type: 'emoticon' | 'image'
  container: StickerItem[]
}

type OwoData = Record<string, StickerGroup>

const loading = ref(false)
const owoData = ref<OwoData>({})
const activeGroup = ref<string>('')
const showAddGroupModal = ref(false)
const showAddStickerModal = ref(false)
const showImportModal = ref(false)

// 附件选择器
const showPreviewSelector = ref(false)
const showOriginSelector = ref(false)

// 多选模式
const selectMode = ref(false)
const selectedIndexes = ref<Set<number>>(new Set())
const lastSelectedIndex = ref<number>(-1)

// 拖拽排序
const dragIndex = ref<number>(-1)
const dragOverIndex = ref<number>(-1)

// 编辑模式
const showEditStickerModal = ref(false)
const editingIndex = ref<number>(-1)
const editSticker = ref({
  text: '',
  previewUrl: '',
  originUrl: '',
  emoticon: ''
})

// 新建分组表单
const newGroup = ref({
  name: '',
  type: 'image' as 'emoticon' | 'image'
})

// 新建表情表单
const newSticker = ref({
  text: '',
  previewUrl: '',
  originUrl: '',
  emoticon: ''
})

// 当前是否在编辑模式（用于附件选择器回调）
const isEditMode = ref(false)

// 打开预览图选择器
const openPreviewSelector = () => {
  isEditMode.value = false
  showAddStickerModal.value = false
  nextTick(() => {
    showPreviewSelector.value = true
  })
}

// 打开原图选择器
const openOriginSelector = () => {
  isEditMode.value = false
  showAddStickerModal.value = false
  nextTick(() => {
    showOriginSelector.value = true
  })
}

// 编辑模式下打开预览图选择器
const openEditPreviewSelector = () => {
  isEditMode.value = true
  showEditStickerModal.value = false
  nextTick(() => {
    showPreviewSelector.value = true
  })
}

// 编辑模式下打开原图选择器
const openEditOriginSelector = () => {
  isEditMode.value = true
  showEditStickerModal.value = false
  nextTick(() => {
    showOriginSelector.value = true
  })
}

// 处理预览图选择
const handlePreviewSelect = (attachments: AttachmentLike[]) => {
  let url = ''
  attachments.forEach((attachment) => {
    if (typeof attachment === 'string') {
      url = attachment
    } else if ('url' in attachment) {
      url = attachment.url
    } else if ('spec' in attachment) {
      url = attachment.status?.permalink || ''
    }
  })
  if (isEditMode.value) {
    editSticker.value.previewUrl = url
  } else {
    newSticker.value.previewUrl = url
  }
  showPreviewSelector.value = false
  nextTick(() => {
    if (isEditMode.value) {
      showEditStickerModal.value = true
    } else {
      showAddStickerModal.value = true
    }
  })
}

// 处理原图选择
const handleOriginSelect = (attachments: AttachmentLike[]) => {
  let url = ''
  attachments.forEach((attachment) => {
    if (typeof attachment === 'string') {
      url = attachment
    } else if ('url' in attachment) {
      url = attachment.url
    } else if ('spec' in attachment) {
      url = attachment.status?.permalink || ''
    }
  })
  if (isEditMode.value) {
    editSticker.value.originUrl = url
  } else {
    newSticker.value.originUrl = url
  }
  showOriginSelector.value = false
  nextTick(() => {
    if (isEditMode.value) {
      showEditStickerModal.value = true
    } else {
      showAddStickerModal.value = true
    }
  })
}

// 处理附件选择器关闭（取消选择时）
const handlePreviewSelectorClose = () => {
  showPreviewSelector.value = false
  nextTick(() => {
    if (isEditMode.value) {
      showEditStickerModal.value = true
    } else {
      showAddStickerModal.value = true
    }
  })
}

const handleOriginSelectorClose = () => {
  showOriginSelector.value = false
  nextTick(() => {
    if (isEditMode.value) {
      showEditStickerModal.value = true
    } else {
      showAddStickerModal.value = true
    }
  })
}

// 导入 JSON
const importJson = ref('')

const groupNames = computed(() => Object.keys(owoData.value))
const currentGroup = computed(() => owoData.value[activeGroup.value])

const loadData = async () => {
  loading.value = true
  try {
    const res = await axios.get('/apis/editor-sticker.ncii.cn/v1alpha1/custom-stickers')
    owoData.value = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
    if (groupNames.value.length > 0 && !activeGroup.value) {
      activeGroup.value = groupNames.value[0]
    }
  } catch (e) {
    console.error('Failed to load stickers', e)
    owoData.value = {}
  } finally {
    loading.value = false
  }
}

const saveData = async () => {
  loading.value = true
  try {
    await axios.post('/apis/editor-sticker.ncii.cn/v1alpha1/custom-stickers', JSON.stringify(owoData.value), {
      headers: { 'Content-Type': 'application/json' }
    })
    Toast.success('保存成功')
  } catch (e) {
    console.error('Failed to save', e)
    Toast.error('保存失败')
  } finally {
    loading.value = false
  }
}


const addGroup = () => {
  if (!newGroup.value.name.trim()) {
    Toast.warning('请输入分组名称')
    return
  }
  if (owoData.value[newGroup.value.name]) {
    Toast.warning('分组已存在')
    return
  }
  owoData.value[newGroup.value.name] = {
    type: newGroup.value.type,
    container: []
  }
  activeGroup.value = newGroup.value.name
  showAddGroupModal.value = false
  newGroup.value = { name: '', type: 'image' }
  saveData()
}

const deleteGroup = (name: string) => {
  Dialog.warning({
    title: '删除分组',
    description: `确定删除分组 "${name}" 吗？该分组下的所有表情都将被删除。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      delete owoData.value[name]
      if (activeGroup.value === name) {
        activeGroup.value = groupNames.value[0] || ''
      }
      await saveData()
    }
  })
}

const addSticker = () => {
  if (!activeGroup.value || !currentGroup.value) return
  
  const group = currentGroup.value
  let icon = ''
  
  if (group.type === 'emoticon') {
    if (!newSticker.value.emoticon.trim()) {
      Toast.warning('请输入颜文字')
      return
    }
    icon = newSticker.value.emoticon
  } else {
    if (!newSticker.value.previewUrl.trim()) {
      Toast.warning('请输入预览图 URL')
      return
    }
    if (!newSticker.value.text.trim()) {
      Toast.warning('请输入表情名称')
      return
    }
    // 构建 icon HTML
    if (newSticker.value.originUrl.trim()) {
      icon = `<img src='${newSticker.value.previewUrl}' origin='${newSticker.value.originUrl}' class="OwO-img">`
    } else {
      icon = `<img src='${newSticker.value.previewUrl}' class="OwO-img">`
    }
  }
  
  group.container.push({
    icon,
    text: group.type === 'emoticon' ? newSticker.value.emoticon : newSticker.value.text
  })
  
  showAddStickerModal.value = false
  newSticker.value = { text: '', previewUrl: '', originUrl: '', emoticon: '' }
  saveData()
}

const deleteSticker = (index: number) => {
  if (!currentGroup.value) return
  Dialog.warning({
    title: '删除表情',
    description: '确定删除这个表情吗？',
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      currentGroup.value?.container.splice(index, 1)
      await saveData()
    }
  })
}

// 从 icon HTML 中提取原图 URL
const getOriginUrl = (icon: string) => {
  const originMatch = icon.match(/origin=['"]([^'"]+)['"]/)
  return originMatch ? originMatch[1] : ''
}

// 打开编辑弹窗
const openEditModal = (index: number) => {
  if (!currentGroup.value) return
  const item = currentGroup.value.container[index]
  editingIndex.value = index
  
  if (currentGroup.value.type === 'emoticon') {
    editSticker.value = {
      text: item.text,
      previewUrl: '',
      originUrl: '',
      emoticon: item.icon
    }
  } else {
    editSticker.value = {
      text: item.text,
      previewUrl: getStickerPreview(item, 'image'),
      originUrl: getOriginUrl(item.icon),
      emoticon: ''
    }
  }
  showEditStickerModal.value = true
}

// 保存编辑
const saveEditSticker = () => {
  if (!currentGroup.value || editingIndex.value < 0) return
  
  const group = currentGroup.value
  let icon = ''
  
  if (group.type === 'emoticon') {
    if (!editSticker.value.emoticon.trim()) {
      Toast.warning('请输入颜文字')
      return
    }
    icon = editSticker.value.emoticon
  } else {
    if (!editSticker.value.previewUrl.trim()) {
      Toast.warning('请输入预览图 URL')
      return
    }
    if (!editSticker.value.text.trim()) {
      Toast.warning('请输入表情名称')
      return
    }
    if (editSticker.value.originUrl.trim()) {
      icon = `<img src='${editSticker.value.previewUrl}' origin='${editSticker.value.originUrl}' class="OwO-img">`
    } else {
      icon = `<img src='${editSticker.value.previewUrl}' class="OwO-img">`
    }
  }
  
  group.container[editingIndex.value] = {
    icon,
    text: group.type === 'emoticon' ? editSticker.value.emoticon : editSticker.value.text
  }
  
  showEditStickerModal.value = false
  editingIndex.value = -1
  editSticker.value = { text: '', previewUrl: '', originUrl: '', emoticon: '' }
  saveData()
}

// 切换选择模式
const toggleSelectMode = () => {
  selectMode.value = !selectMode.value
  if (!selectMode.value) {
    selectedIndexes.value.clear()
    lastSelectedIndex.value = -1
  }
}

// 切换选中状态（支持 Shift 连续选择）
const toggleSelect = (index: number, event?: MouseEvent) => {
  // Shift + 点击：连续选择
  if (event?.shiftKey && lastSelectedIndex.value !== -1) {
    const start = Math.min(lastSelectedIndex.value, index)
    const end = Math.max(lastSelectedIndex.value, index)
    for (let i = start; i <= end; i++) {
      selectedIndexes.value.add(i)
    }
  } else {
    // 普通点击：切换选中状态
    if (selectedIndexes.value.has(index)) {
      selectedIndexes.value.delete(index)
    } else {
      selectedIndexes.value.add(index)
    }
    lastSelectedIndex.value = index
  }
}

// 全选/取消全选
const toggleSelectAll = () => {
  if (!currentGroup.value) return
  if (selectedIndexes.value.size === currentGroup.value.container.length) {
    selectedIndexes.value.clear()
  } else {
    selectedIndexes.value = new Set(currentGroup.value.container.map((_, i) => i))
  }
}

// 批量删除
const deleteSelected = () => {
  if (!currentGroup.value || selectedIndexes.value.size === 0) return
  Dialog.warning({
    title: '批量删除',
    description: `确定删除选中的 ${selectedIndexes.value.size} 个表情吗？`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      // 从大到小排序，避免删除时索引变化
      const indexes = Array.from(selectedIndexes.value).sort((a, b) => b - a)
      indexes.forEach((index) => {
        currentGroup.value?.container.splice(index, 1)
      })
      selectedIndexes.value.clear()
      selectMode.value = false
      await saveData()
    }
  })
}

// 移动选中项到顶部
const moveSelectedToTop = async () => {
  if (!currentGroup.value || selectedIndexes.value.size === 0) return
  
  const container = currentGroup.value.container
  const indexes = Array.from(selectedIndexes.value).sort((a, b) => a - b)
  const items = indexes.map(i => container[i])
  
  // 从大到小删除，避免索引变化
  Array.from(selectedIndexes.value).sort((a, b) => b - a).forEach(i => {
    container.splice(i, 1)
  })
  
  // 插入到顶部
  container.unshift(...items)
  
  // 更新选中索引
  selectedIndexes.value = new Set(items.map((_, i) => i))
  await saveData()
}

// 移动选中项到底部
const moveSelectedToBottom = async () => {
  if (!currentGroup.value || selectedIndexes.value.size === 0) return
  
  const container = currentGroup.value.container
  const indexes = Array.from(selectedIndexes.value).sort((a, b) => a - b)
  const items = indexes.map(i => container[i])
  
  // 从大到小删除，避免索引变化
  Array.from(selectedIndexes.value).sort((a, b) => b - a).forEach(i => {
    container.splice(i, 1)
  })
  
  // 插入到底部
  container.push(...items)
  
  // 更新选中索引
  const startIndex = container.length - items.length
  selectedIndexes.value = new Set(items.map((_, i) => startIndex + i))
  await saveData()
}

// 拖拽开始
const handleDragStart = (e: DragEvent, index: number) => {
  // 多选模式下，如果拖拽的是选中项，则拖拽所有选中项
  if (selectMode.value && selectedIndexes.value.has(index)) {
    dragIndex.value = index
    // 设置拖拽数据
    if (e.dataTransfer) {
      e.dataTransfer.effectAllowed = 'move'
      e.dataTransfer.setData('text/plain', Array.from(selectedIndexes.value).join(','))
    }
  } else if (!selectMode.value) {
    dragIndex.value = index
  }
}

// 拖拽经过
const handleDragOver = (e: DragEvent, index: number) => {
  e.preventDefault()
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = 'move'
  }
  dragOverIndex.value = index
}

// 拖拽离开
const handleDragLeave = () => {
  dragOverIndex.value = -1
}

// 拖拽结束
const handleDragEnd = () => {
  dragIndex.value = -1
  dragOverIndex.value = -1
}

// 放置
const handleDrop = async (e: DragEvent, index: number) => {
  e.preventDefault()
  
  if (!currentGroup.value || dragIndex.value === -1) {
    dragIndex.value = -1
    dragOverIndex.value = -1
    return
  }
  
  const container = currentGroup.value.container
  
  // 多选模式下拖拽选中项
  if (selectMode.value && selectedIndexes.value.size > 0 && selectedIndexes.value.has(dragIndex.value)) {
    const indexes = Array.from(selectedIndexes.value).sort((a, b) => a - b)
    
    // 如果目标位置在选中项中，不做任何操作
    if (selectedIndexes.value.has(index)) {
      dragIndex.value = -1
      dragOverIndex.value = -1
      return
    }
    
    const items = indexes.map(i => container[i])
    
    // 计算目标位置（考虑删除后的索引变化）
    let targetIndex = index
    const deletedBefore = indexes.filter(i => i < index).length
    targetIndex -= deletedBefore
    
    // 从大到小删除
    indexes.sort((a, b) => b - a).forEach(i => {
      container.splice(i, 1)
    })
    
    // 插入到目标位置
    container.splice(targetIndex, 0, ...items)
    
    // 更新选中索引
    selectedIndexes.value = new Set(items.map((_, i) => targetIndex + i))
  } else if (!selectMode.value) {
    // 单个拖拽
    if (dragIndex.value === index) {
      dragIndex.value = -1
      dragOverIndex.value = -1
      return
    }
    
    const item = container.splice(dragIndex.value, 1)[0]
    const targetIndex = dragIndex.value < index ? index - 1 : index
    container.splice(targetIndex, 0, item)
  }
  
  dragIndex.value = -1
  dragOverIndex.value = -1
  await saveData()
}

const getStickerPreview = (item: StickerItem, type: string) => {
  if (type === 'emoticon') {
    return item.icon
  }
  const srcMatch = item.icon.match(/src=['"]([^'"]+)['"]/)
  return srcMatch ? srcMatch[1] : ''
}

const importFromJson = async () => {
  try {
    const data = JSON.parse(importJson.value)
    owoData.value = data
    showImportModal.value = false
    importJson.value = ''
    await saveData()
    if (groupNames.value.length > 0) {
      activeGroup.value = groupNames.value[0]
    }
  } catch (e) {
    console.error('Import failed', e)
    Toast.error('JSON 格式错误')
  }
}

const exportJson = () => {
  const json = JSON.stringify(owoData.value, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'OwO.json'
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(loadData)
</script>


<template>
  <div class="sticker-manager">
    <VCard :title="'自定义表情管理'" :loading="loading">
      <template #actions>
        <VSpace>
          <VButton size="sm" @click="showImportModal = true">导入 JSON</VButton>
          <VButton size="sm" @click="exportJson">导出 JSON</VButton>
          <VButton type="primary" size="sm" @click="showAddGroupModal = true">
            <template #icon><IconAddCircle /></template>
            新建分组
          </VButton>
        </VSpace>
      </template>
      
      <div class="manager-content">
        <!-- 分组列表 -->
        <div class="group-sidebar">
          <div class="group-list">
            <div
              v-for="name in groupNames"
              :key="name"
              class="group-item"
              :class="{ active: activeGroup === name }"
              @click="activeGroup = name"
            >
              <span class="group-name">{{ name }}</span>
              <span class="group-type">{{ owoData[name].type === 'emoticon' ? '颜文字' : '图片' }}</span>
              <button class="delete-btn" @click.stop="deleteGroup(name)">
                <IconDeleteBin />
              </button>
            </div>
          </div>
        </div>
        
        <!-- 表情列表 -->
        <div class="sticker-content">
          <div v-if="!activeGroup" class="empty-tip">请选择或创建一个分组</div>
          <template v-else-if="currentGroup">
            <div class="sticker-header">
              <span>{{ activeGroup }} ({{ currentGroup.container.length }} 个表情)<template v-if="selectMode && selectedIndexes.size > 0">，已选 {{ selectedIndexes.size }} 个</template></span>
              <VSpace>
                <template v-if="selectMode">
                  <VButton size="sm" @click="toggleSelectAll">
                    {{ selectedIndexes.size === currentGroup.container.length ? '取消全选' : '全选' }}
                  </VButton>
                  <VButton size="sm" :disabled="selectedIndexes.size === 0" @click="moveSelectedToTop">
                    移至顶部
                  </VButton>
                  <VButton size="sm" :disabled="selectedIndexes.size === 0" @click="moveSelectedToBottom">
                    移至底部
                  </VButton>
                  <VButton size="sm" type="danger" :disabled="selectedIndexes.size === 0" @click="deleteSelected">
                    删除 ({{ selectedIndexes.size }})
                  </VButton>
                  <VButton size="sm" @click="toggleSelectMode">取消</VButton>
                </template>
                <template v-else>
                  <VButton v-if="currentGroup.container.length > 0" size="sm" @click="toggleSelectMode">多选</VButton>
                  <VButton size="sm" type="primary" @click="showAddStickerModal = true">
                    <template #icon><IconAddCircle /></template>
                    添加表情
                  </VButton>
                </template>
              </VSpace>
            </div>
            <div class="sticker-grid">
              <div
                v-for="(item, index) in currentGroup.container"
                :key="index"
                class="sticker-item"
                :class="{ 
                  selected: selectMode && selectedIndexes.has(index),
                  dragging: dragIndex === index || (selectMode && selectedIndexes.has(index) && dragIndex !== -1 && selectedIndexes.has(dragIndex)),
                  'drag-over': dragOverIndex === index && dragIndex !== index && !(selectMode && selectedIndexes.has(index))
                }"
                :draggable="!selectMode || selectedIndexes.has(index)"
                @click="selectMode ? toggleSelect(index, $event) : undefined"
                @dragstart="handleDragStart($event, index)"
                @dragover="handleDragOver($event, index)"
                @dragleave="handleDragLeave"
                @dragend="handleDragEnd"
                @drop="handleDrop($event, index)"
              >
                <div v-if="selectMode" class="select-checkbox" :class="{ checked: selectedIndexes.has(index) }">
                  <span v-if="selectedIndexes.has(index)">✓</span>
                </div>
                <div v-if="!selectMode" class="drag-handle" title="拖拽排序">⋮⋮</div>
                <template v-if="currentGroup.type === 'emoticon'">
                  <span class="emoticon">{{ item.icon }}</span>
                </template>
                <template v-else>
                  <img :src="getStickerPreview(item, currentGroup.type)" :alt="item.text" />
                </template>
                <span class="sticker-name">{{ item.text }}</span>
                <div v-if="!selectMode" class="action-btns">
                  <button class="edit-btn" @click.stop="openEditModal(index)">
                    <IconEdit />
                  </button>
                  <button class="delete-btn" @click.stop="deleteSticker(index)">
                    <IconDeleteBin />
                  </button>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </VCard>
    
    <!-- 新建分组弹窗 -->
    <VModal v-model:visible="showAddGroupModal" title="新建分组" @close="showAddGroupModal = false">
      <div class="form-item">
        <label>分组名称</label>
        <input v-model="newGroup.name" class="form-input" placeholder="输入分组名称" />
      </div>
      <div class="form-item">
        <label>类型</label>
        <select v-model="newGroup.type" class="form-select">
          <option value="image">图片表情</option>
          <option value="emoticon">颜文字</option>
        </select>
      </div>
      <template #footer>
        <VSpace>
          <VButton @click="showAddGroupModal = false">取消</VButton>
          <VButton type="primary" @click="addGroup">确定</VButton>
        </VSpace>
      </template>
    </VModal>

    
    <!-- 添加表情弹窗 -->
    <VModal v-model:visible="showAddStickerModal" title="添加表情" @close="showAddStickerModal = false">
      <template v-if="currentGroup?.type === 'emoticon'">
        <div class="form-item">
          <label>颜文字</label>
          <input v-model="newSticker.emoticon" class="form-input" placeholder="输入颜文字，如 (⌐■_■)" />
        </div>
      </template>
      <template v-else>
        <div class="form-item">
          <label>表情名称</label>
          <input v-model="newSticker.text" class="form-input" placeholder="输入表情名称" />
        </div>
        <div class="form-item">
          <label>预览图</label>
          <div class="upload-row">
            <input v-model="newSticker.previewUrl" class="form-input" placeholder="输入 URL 或从附件库选择" />
            <VButton size="sm" @click="openPreviewSelector">选择</VButton>
          </div>
        </div>
        <div class="form-item">
          <label>原图（可选，用于文章显示）</label>
          <div class="upload-row">
            <input v-model="newSticker.originUrl" class="form-input" placeholder="输入 URL 或从附件库选择，留空则使用预览图" />
            <VButton size="sm" @click="openOriginSelector">选择</VButton>
          </div>
        </div>
        <div v-if="newSticker.previewUrl" class="preview-box">
          <img :src="newSticker.previewUrl" alt="预览" />
        </div>
      </template>
      <template #footer>
        <VSpace>
          <VButton @click="showAddStickerModal = false">取消</VButton>
          <VButton type="primary" @click="addSticker">确定</VButton>
        </VSpace>
      </template>
    </VModal>
    
    <!-- 编辑表情弹窗 -->
    <VModal v-model:visible="showEditStickerModal" title="编辑表情" @close="showEditStickerModal = false">
      <template v-if="currentGroup?.type === 'emoticon'">
        <div class="form-item">
          <label>颜文字</label>
          <input v-model="editSticker.emoticon" class="form-input" placeholder="输入颜文字，如 (⌐■_■)" />
        </div>
      </template>
      <template v-else>
        <div class="form-item">
          <label>表情名称</label>
          <input v-model="editSticker.text" class="form-input" placeholder="输入表情名称" />
        </div>
        <div class="form-item">
          <label>预览图</label>
          <div class="upload-row">
            <input v-model="editSticker.previewUrl" class="form-input" placeholder="输入 URL 或从附件库选择" />
            <VButton size="sm" @click="openEditPreviewSelector">选择</VButton>
          </div>
        </div>
        <div class="form-item">
          <label>原图（可选，用于文章显示）</label>
          <div class="upload-row">
            <input v-model="editSticker.originUrl" class="form-input" placeholder="输入 URL 或从附件库选择，留空则使用预览图" />
            <VButton size="sm" @click="openEditOriginSelector">选择</VButton>
          </div>
        </div>
        <div v-if="editSticker.previewUrl" class="preview-box">
          <img :src="editSticker.previewUrl" alt="预览" />
        </div>
      </template>
      <template #footer>
        <VSpace>
          <VButton @click="showEditStickerModal = false">取消</VButton>
          <VButton type="primary" @click="saveEditSticker">保存</VButton>
        </VSpace>
      </template>
    </VModal>
    
    <!-- 导入 JSON 弹窗 -->
    <VModal v-model:visible="showImportModal" title="导入 JSON" @close="showImportModal = false">
      <div class="form-item">
        <label>粘贴 OwO.json 内容</label>
        <textarea v-model="importJson" class="json-textarea" rows="10" placeholder="粘贴 JSON 内容"></textarea>
      </div>
      <template #footer>
        <VSpace>
          <VButton @click="showImportModal = false">取消</VButton>
          <VButton type="primary" @click="importFromJson">导入</VButton>
        </VSpace>
      </template>
    </VModal>
    
    <!-- 预览图附件选择器 -->
    <AttachmentSelectorModal
      v-model:visible="showPreviewSelector"
      :accepts="['image/*']"
      :max="1"
      @select="handlePreviewSelect"
      @close="handlePreviewSelectorClose"
    />
    
    <!-- 原图附件选择器 -->
    <AttachmentSelectorModal
      v-model:visible="showOriginSelector"
      :accepts="['image/*']"
      :max="1"
      @select="handleOriginSelect"
      @close="handleOriginSelectorClose"
    />
  </div>
</template>

<style lang="scss" scoped>
.sticker-manager {
  padding: 16px;
}

.manager-content {
  display: flex;
  gap: 16px;
  min-height: 400px;
}

.group-sidebar {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid #e5e7eb;
  padding-right: 16px;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.group-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
  
  &:hover {
    background: #f3f4f6;
  }
  
  &.active {
    background: #e5e7eb;
  }
  
  .group-name {
    flex: 1;
    font-weight: 500;
  }
  
  .group-type {
    font-size: 12px;
    color: #9ca3af;
    margin-right: 8px;
  }
  
  .delete-btn {
    opacity: 0;
    background: none;
    border: none;
    cursor: pointer;
    color: #ef4444;
    padding: 4px;
    
    &:hover {
      color: #dc2626;
    }
  }
  
  &:hover .delete-btn {
    opacity: 1;
  }
}

.sticker-content {
  flex: 1;
  min-width: 0;
}

.sticker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-weight: 500;
}

.sticker-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
  gap: 12px;
}

.sticker-item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: default;
  transition: all 0.2s;
  
  &.selected {
    border-color: #3b82f6;
    background: #eff6ff;
  }
  
  &.dragging {
    opacity: 0.5;
    border-style: dashed;
  }
  
  &.drag-over {
    border-color: #3b82f6;
    background: #dbeafe;
    transform: scale(1.02);
  }
  
  &[draggable="true"] {
    cursor: grab;
    
    &:active {
      cursor: grabbing;
    }
  }
  
  .drag-handle {
    position: absolute;
    top: 2px;
    left: 50%;
    transform: translateX(-50%);
    font-size: 10px;
    color: #d1d5db;
    letter-spacing: -2px;
    cursor: grab;
    user-select: none;
    
    &:active {
      cursor: grabbing;
    }
  }
  
  &:hover .drag-handle {
    color: #9ca3af;
  }
  
  img {
    width: 48px;
    height: 48px;
    object-fit: contain;
    pointer-events: none;
  }
  
  .emoticon {
    font-size: 24px;
    height: 48px;
    display: flex;
    align-items: center;
    pointer-events: none;
  }
  
  .sticker-name {
    margin-top: 4px;
    font-size: 12px;
    color: #6b7280;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    pointer-events: none;
  }
  
  .select-checkbox {
    position: absolute;
    top: 4px;
    left: 4px;
    width: 18px;
    height: 18px;
    border: 2px solid #d1d5db;
    border-radius: 4px;
    background: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: #fff;
    
    &.checked {
      background: #3b82f6;
      border-color: #3b82f6;
    }
  }
  
  .action-btns {
    position: absolute;
    top: 4px;
    right: 4px;
    display: flex;
    gap: 2px;
    opacity: 0;
    transition: opacity 0.2s;
  }
  
  .edit-btn,
  .delete-btn {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    cursor: pointer;
    padding: 2px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  
  .edit-btn {
    color: #3b82f6;
    
    &:hover {
      color: #2563eb;
    }
  }
  
  .delete-btn {
    color: #ef4444;
    
    &:hover {
      color: #dc2626;
    }
  }
  
  &:hover .action-btns {
    opacity: 1;
  }
}

.empty-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #9ca3af;
}

.form-item {
  margin-bottom: 16px;
  
  label {
    display: block;
    margin-bottom: 8px;
    font-weight: 500;
  }
}

.form-input,
.form-select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
  
  &:focus {
    border-color: #3b82f6;
  }
}

.form-select {
  background: #fff;
  cursor: pointer;
}

.json-textarea {
  width: 100%;
  padding: 8px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  font-family: monospace;
  resize: vertical;
}

.upload-row {
  display: flex;
  gap: 8px;
  
  .form-input {
    flex: 1;
  }
}

.preview-box {
  margin-top: 12px;
  padding: 12px;
  background: #f9fafb;
  border-radius: 6px;
  text-align: center;
  
  img {
    max-width: 100px;
    max-height: 100px;
  }
}
</style>
