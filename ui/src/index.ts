import { definePlugin } from '@halo-dev/console-shared'
import { createApp, type App, markRaw } from 'vue'
import { StickerExtension } from './extensions/sticker-extension'
import VditorStickerInjector from './components/VditorStickerInjector.vue'
import BytemdStickerInjector from './components/BytemdStickerInjector.vue'
import WillowStickerInjector from './components/WillowStickerInjector.vue'
import StickerManager from './views/StickerManager.vue'

// 用于编辑器注入的容器
let vditorInjectorApp: App | null = null
let vditorInjectorContainer: HTMLElement | null = null
let bytemdInjectorApp: App | null = null
let bytemdInjectorContainer: HTMLElement | null = null
let willowInjectorApp: App | null = null
let willowInjectorContainer: HTMLElement | null = null

const initInjectors = () => {
  // Vditor 注入器
  if (!document.getElementById('vditor-sticker-injector')) {
    vditorInjectorContainer = document.createElement('div')
    vditorInjectorContainer.id = 'vditor-sticker-injector'
    document.body.appendChild(vditorInjectorContainer)
    vditorInjectorApp = createApp(VditorStickerInjector)
    vditorInjectorApp.mount(vditorInjectorContainer)
  }

  // ByteMD 注入器
  if (!document.getElementById('bytemd-sticker-injector')) {
    bytemdInjectorContainer = document.createElement('div')
    bytemdInjectorContainer.id = 'bytemd-sticker-injector'
    document.body.appendChild(bytemdInjectorContainer)
    bytemdInjectorApp = createApp(BytemdStickerInjector)
    bytemdInjectorApp.mount(bytemdInjectorContainer)
  }

  // Willow MDE 注入器
  if (!document.getElementById('willow-sticker-injector')) {
    willowInjectorContainer = document.createElement('div')
    willowInjectorContainer.id = 'willow-sticker-injector'
    document.body.appendChild(willowInjectorContainer)
    willowInjectorApp = createApp(WillowStickerInjector)
    willowInjectorApp.mount(willowInjectorContainer)
  }
}

// 延迟初始化，确保 DOM 已准备好
if (typeof window !== 'undefined') {
  // 使用 setTimeout 确保在 Halo 框架加载完成后执行
  setTimeout(() => {
    initInjectors()
  }, 100)
}

export default definePlugin({
  components: {},
  routes: [],
  extensionPoints: {
    'default:editor:extension:create': () => {
      // 确保注入器已初始化
      initInjectors()
      return [StickerExtension]
    },
    'plugin:self:tabs:create': () => {
      return [
        {
          id: 'sticker-manager',
          label: '表情管理',
          component: markRaw(StickerManager),
          priority: 10,
        },
      ]
    },
  },
})
