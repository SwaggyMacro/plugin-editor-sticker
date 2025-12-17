export interface Sticker {
  name: string
  url: string // 预览图 URL（用于面板显示）
  originUrl?: string // 原图 URL（用于插入文章）
  alt?: string
  // 短代码，如 :alu_暗地观察:
  shortcode?: string
}

export interface StickerGroup {
  name: string
  icon?: string
  // 分组前缀，用于生成短代码，如 "alu" -> :alu_xxx:
  prefix?: string
  stickers: Sticker[]
}

export interface StickerConfig {
  groups: StickerGroup[]
}
