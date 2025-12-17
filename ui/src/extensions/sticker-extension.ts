import { Extension, type Editor } from '@halo-dev/richtext-editor'
import type { ExtensionOptions, ToolbarItemType, ToolboxItemType } from '@halo-dev/richtext-editor'
import { markRaw, type Component } from 'vue'
import StickerToolbarItem from '@/components/StickerToolbarItem.vue'

// 扩展选项
const stickerExtensionOptions: ExtensionOptions = {
  // 工具栏项
  getToolbarItems({ editor }: { editor: Editor }): ToolbarItemType[] {
    return [
      {
        priority: 200,
        component: markRaw(StickerToolbarItem as Component),
        props: {
          editor,
          isActive: false,
        },
      },
    ]
  },

  // 工具箱项
  getToolboxItems({ editor }: { editor: Editor }): ToolboxItemType[] {
    return [
      {
        priority: 200,
        component: markRaw(StickerToolbarItem as Component),
        props: {
          editor,
          title: '表情',
          description: '插入表情包',
        },
      },
    ]
  },
}

// 创建 TipTap 扩展
export const StickerExtension = Extension.create({
  name: 'sticker',
  addOptions() {
    return {
      ...stickerExtensionOptions,
    }
  },
})

export default StickerExtension
