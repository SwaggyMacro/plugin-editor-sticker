import { ref } from 'vue'
import type { StickerGroup, Sticker } from '@/types/sticker'
import axios from 'axios'

// 插件配置
interface PluginConfig {
  enableCustomMode?: boolean
  stickerConfigUrl?: string
  enableDefaultEditor?: boolean
  enableVditorEditor?: boolean
  enableBytemdEditor?: boolean
  enableWillowEditor?: boolean
}

const pluginConfig = ref<PluginConfig>({})

// 默认的表情包配置 (OwO 格式)
const defaultStickerGroups: StickerGroup[] = [
  {
    name: 'Emoji',
    icon: '',
    stickers: [
      { name: '😀', url: '', alt: '😀' },
      { name: '😂', url: '', alt: '😂' },
      { name: '😍', url: '', alt: '😍' },
      { name: '🤔', url: '', alt: '🤔' },
      { name: '😎', url: '', alt: '😎' },
      { name: '😢', url: '', alt: '😢' },
      { name: '😡', url: '', alt: '😡' },
      { name: '👍', url: '', alt: '👍' },
      { name: '👎', url: '', alt: '👎' },
      { name: '❤️', url: '', alt: '❤️' },
      { name: '🎉', url: '', alt: '🎉' },
      { name: '🔥', url: '', alt: '🔥' },
      { name: '✨', url: '', alt: '✨' },
      { name: '💯', url: '', alt: '💯' },
      { name: '🤣', url: '', alt: '🤣' },
      { name: '😊', url: '', alt: '😊' },
      { name: '🥰', url: '', alt: '🥰' },
      { name: '😘', url: '', alt: '😘' },
      { name: '🤗', url: '', alt: '🤗' },
      { name: '🤩', url: '', alt: '🤩' },
      { name: '😏', url: '', alt: '😏' },
      { name: '😒', url: '', alt: '😒' },
      { name: '🙄', url: '', alt: '🙄' },
      { name: '😴', url: '', alt: '😴' },
    ],
  },
  {
    name: '颜文字',
    icon: '',
    stickers: [
      { name: '(⌐■_■)', url: '', alt: '(⌐■_■)' },
      { name: '( ´_ゝ`)', url: '', alt: '( ´_ゝ`)' },
      { name: '( ͡° ͜ʖ ͡°)', url: '', alt: '( ͡° ͜ʖ ͡°)' },
      { name: '(╯°□°）╯︵ ┻━┻', url: '', alt: '(╯°□°）╯︵ ┻━┻' },
      { name: '┬─┬ノ( º _ ºノ)', url: '', alt: '┬─┬ノ( º _ ºノ)' },
      { name: '¯\\_(ツ)_/¯', url: '', alt: '¯\\_(ツ)_/¯' },
      { name: '(ノಠ益ಠ)ノ彡┻━┻', url: '', alt: '(ノಠ益ಠ)ノ彡┻━┻' },
      { name: '(づ｡◕‿‿◕｡)づ', url: '', alt: '(づ｡◕‿‿◕｡)づ' },
      { name: '(｡◕‿◕｡)', url: '', alt: '(｡◕‿◕｡)' },
      { name: '(╥﹏╥)', url: '', alt: '(╥﹏╥)' },
      { name: '(ಥ_ಥ)', url: '', alt: '(ಥ_ಥ)' },
      { name: '(◕‿◕)', url: '', alt: '(◕‿◕)' },
      { name: '(｀・ω・´)', url: '', alt: '(｀・ω・´)' },
      { name: '(´・ω・`)', url: '', alt: '(´・ω・`)' },
      { name: '(=^･ω･^=)', url: '', alt: '(=^･ω･^=)' },
      { name: '(●\'◡\'●)', url: '', alt: '(●\'◡\'●)' },
    ],
  },
]

const stickerGroups = ref<StickerGroup[]>([])
const loading = ref(false)
const loaded = ref(false)

export function useStickers() {
  const loadPluginConfig = async (): Promise<PluginConfig> => {
    try {
      const response = await axios.get('/api/v1alpha1/configmaps/editor-sticker-configmap')
      const configMap = response.data
      if (configMap?.data?.basic) {
        const basicConfig = JSON.parse(configMap.data.basic)
        pluginConfig.value = basicConfig
        return basicConfig
      }
    } catch (err) {
      console.warn('[editor-sticker] Failed to load plugin config:', err)
    }
    return {}
  }

  const loadStickers = async (configUrl?: string) => {
    if (loaded.value) return

    loading.value = true
    try {
      // 如果配置还没加载，先加载
      let config = pluginConfig.value
      if (Object.keys(config).length === 0) {
        config = await loadPluginConfig()
      }
      
      // 自定义模式：从自定义数据加载
      if (config.enableCustomMode) {
        console.log('[editor-sticker] Loading stickers from custom mode')
        try {
          const customRes = await axios.get('/apis/editor-sticker.ncii.cn/v1alpha1/custom-stickers')
          const customData = typeof customRes.data === 'string' ? JSON.parse(customRes.data) : customRes.data
          if (Object.keys(customData).length > 0) {
            stickerGroups.value = parseOwoConfig(customData)
            loaded.value = true
            return
          }
        } catch (e) {
          console.warn('[editor-sticker] Failed to load custom stickers, fallback to default')
        }
      }
      
      if (configUrl) {
        const response = await axios.get(configUrl)
        const owoData = response.data
        stickerGroups.value = parseOwoConfig(owoData)
      } else {
        // 使用配置中的 URL
        if (config.stickerConfigUrl) {
          console.log('[editor-sticker] Loading stickers from:', config.stickerConfigUrl)
          const configResponse = await axios.get(config.stickerConfigUrl)
          console.log('[editor-sticker] OwO data loaded:', configResponse.data)
          stickerGroups.value = parseOwoConfig(configResponse.data)
          loaded.value = true
          return
        }
        stickerGroups.value = defaultStickerGroups
      }
      loaded.value = true
    } catch (error) {
      console.error('Failed to load stickers:', error)
      stickerGroups.value = defaultStickerGroups
      loaded.value = true
    } finally {
      loading.value = false
    }
  }
  
  const isDefaultEditorEnabled = () => {
    return pluginConfig.value.enableDefaultEditor !== false
  }
  
  const isVditorEditorEnabled = () => {
    return pluginConfig.value.enableVditorEditor !== false
  }

  const isBytemdEditorEnabled = () => {
    return pluginConfig.value.enableBytemdEditor !== false
  }

  const isWillowEditorEnabled = () => {
    return pluginConfig.value.enableWillowEditor !== false
  }

  // OwO 容器项类型
  interface OwoContainerItem {
    icon: string
    text: string
  }

  // OwO 分组类型
  interface OwoGroup {
    type: 'emoticon' | 'image'
    container: OwoContainerItem[]
  }

  // 生成短代码前缀 - 直接使用分组名，保持与后端一致
  const generatePrefix = (groupName: string): string => {
    // 直接使用分组名，只替换空格为下划线
    return groupName.replace(/\s+/g, '_')
  }

  // 解析 OwO 格式的配置 (支持两种格式)
  const parseOwoConfig = (
    owoData: Record<string, OwoGroup | Record<string, string>>
  ): StickerGroup[] => {
    const groups: StickerGroup[] = []

    for (const [groupName, groupData] of Object.entries(owoData)) {
      const stickerList: Sticker[] = []
      const prefix = generatePrefix(groupName)

      // 检测是否为新格式 (带 type 和 container)
      if (
        groupData &&
        typeof groupData === 'object' &&
        'type' in groupData &&
        'container' in groupData
      ) {
        const owoGroup = groupData as OwoGroup
        const isImage = owoGroup.type === 'image'

        for (const item of owoGroup.container) {
          if (isImage) {
            // 优先从 origin 属性提取原图 URL，否则从 src 提取
            const originMatch = item.icon.match(/origin=["']([^"']+)["']/)
            const srcMatch = item.icon.match(/src=["']([^"']+)["']/)
            // 原图 URL（用于插入文章）
            const originUrl = originMatch ? originMatch[1] : (srcMatch ? srcMatch[1] : '')
            // 预览图 URL（用于面板显示）
            const previewUrl = srcMatch ? srcMatch[1] : originUrl
            // 处理协议相对 URL
            const fullOriginUrl = originUrl.startsWith('//') ? 'https:' + originUrl : originUrl
            const fullPreviewUrl = previewUrl.startsWith('//') ? 'https:' + previewUrl : previewUrl
            // 生成短代码
            const shortcode = `:${prefix}_${item.text}:`
            stickerList.push({
              name: item.text,
              url: fullPreviewUrl, // 面板显示用预览图
              originUrl: fullOriginUrl, // 原图 URL
              alt: item.text,
              shortcode,
            })
          } else {
            // 颜文字类型 - 直接使用文本，不需要短代码
            stickerList.push({
              name: item.icon,
              url: '',
              alt: item.icon,
              shortcode: item.icon, // 颜文字直接用原文
            })
          }
        }
      } else {
        // 旧格式: { "name": "url" }
        for (const [name, value] of Object.entries(groupData as Record<string, string>)) {
          const isUrl =
            typeof value === 'string' && (value.startsWith('http') || value.startsWith('/'))
          const shortcode = isUrl ? `:${prefix}_${name}:` : value
          stickerList.push({
            name,
            url: isUrl ? value : '',
            alt: isUrl ? name : value,
            shortcode,
          })
        }
      }

      groups.push({
        name: groupName,
        prefix,
        stickers: stickerList,
      })
    }

    return groups
  }

  const reloadStickers = async (configUrl?: string) => {
    loaded.value = false
    await loadStickers(configUrl)
  }

  return {
    stickerGroups,
    loading,
    loaded,
    pluginConfig,
    loadStickers,
    reloadStickers,
    loadPluginConfig,
    isDefaultEditorEnabled,
    isVditorEditorEnabled,
    isBytemdEditorEnabled,
    isWillowEditorEnabled,
  }
}
