package cn.ncii.editorsticker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.theme.ReactivePostContentHandler;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章内容处理器 - 在服务端将短代码替换为图片
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StickerContentHandler implements ReactivePostContentHandler {

    private final ReactiveSettingFetcher settingFetcher;
    private final run.halo.app.extension.ReactiveExtensionClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder()
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
        .build();
    
    // 缓存表情映射
    private final Map<String, String> stickerCache = new ConcurrentHashMap<>();
    private volatile String cachedConfigUrl = null;
    private volatile String cachedExtraStyle = "";
    
    // 短代码正则: :prefix_name: (支持字母、数字、下划线、连字符、中文，允许冒号内侧有空格)
    // 使用负向后行断言排除转义的冒号 \:
    private static final Pattern SHORTCODE_PATTERN = Pattern.compile("(?<!\\\\):\\s*([a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+)\\s*:(?!\\\\)");
    // 转义短代码正则: \:xxx\: 或 \:xxx: 或 :xxx\:
    private static final Pattern ESCAPED_SHORTCODE_PATTERN = Pattern.compile("\\\\:([a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+)\\\\?:|:([a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+)\\\\:");

    @Override
    public Mono<PostContentContext> handle(PostContentContext context) {
        return loadStickerMapReactive()
            .map(stickerMap -> {
                if (stickerMap.isEmpty()) {
                    return context;
                }
                
                String content = context.getContent();
                String processedContent = replaceShortcodes(content, stickerMap);
                context.setContent(processedContent);
                return context;
            })
            .onErrorResume(e -> {
                log.error("Failed to process stickers", e);
                return Mono.just(context);
            });
    }

    
    private Mono<Map<String, String>> loadStickerMapReactive() {
        return settingFetcher.get("basic")
            .flatMap(setting -> {
                // 缓存额外样式
                if (setting.has("stickerStyle")) {
                    cachedExtraStyle = setting.get("stickerStyle").asText("");
                }
                
                // 检查是否启用自定义模式
                boolean enableCustomMode = setting.has("enableCustomMode") && setting.get("enableCustomMode").asBoolean(false);
                
                if (enableCustomMode) {
                    return loadCustomStickers();
                }
                
                if (!setting.has("stickerConfigUrl")) {
                    return Mono.just(Map.<String, String>of());
                }
                
                String configUrl = setting.get("stickerConfigUrl").asText("");
                if (configUrl == null || configUrl.isEmpty()) {
                    return Mono.just(Map.<String, String>of());
                }
                
                // 如果配置没变且缓存存在，直接返回缓存
                if (configUrl.equals(cachedConfigUrl) && !stickerCache.isEmpty()) {
                    return Mono.just(new HashMap<>(stickerCache));
                }
                
                // 加载新配置
                return webClient.get()
                    .uri(configUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .map(body -> {
                        try {
                            JsonNode root = objectMapper.readTree(body);
                            Map<String, String> map = parseOwoConfig(root);
                            stickerCache.clear();
                            stickerCache.putAll(map);
                            cachedConfigUrl = configUrl;
                            log.info("Loaded {} stickers from {}", map.size(), configUrl);
                            return map;
                        } catch (Exception e) {
                            log.error("Failed to parse sticker config", e);
                            return stickerCache.isEmpty() ? Map.<String, String>of() : new HashMap<>(stickerCache);
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Failed to fetch sticker config from {}", configUrl, e);
                        return Mono.just(stickerCache.isEmpty() ? Map.<String, String>of() : new HashMap<>(stickerCache));
                    });
            })
            .switchIfEmpty(Mono.just(Map.of()));
    }
    
    private Mono<Map<String, String>> loadCustomStickers() {
        return client.fetch(run.halo.app.extension.ConfigMap.class, "editor-sticker-custom-data")
            .map(configMap -> {
                try {
                    String data = configMap.getData().get("stickers");
                    if (data == null || data.isEmpty()) {
                        return Map.<String, String>of();
                    }
                    JsonNode root = objectMapper.readTree(data);
                    Map<String, String> map = parseOwoConfig(root);
                    stickerCache.clear();
                    stickerCache.putAll(map);
                    cachedConfigUrl = "custom";
                    log.info("Loaded {} custom stickers", map.size());
                    return map;
                } catch (Exception e) {
                    log.error("Failed to parse custom stickers", e);
                    return stickerCache.isEmpty() ? Map.<String, String>of() : new HashMap<>(stickerCache);
                }
            })
            .defaultIfEmpty(Map.of());
    }
    
    private Map<String, String> parseOwoConfig(JsonNode root) {
        Map<String, String> map = new HashMap<>();
        
        root.fields().forEachRemaining(entry -> {
            String groupName = entry.getKey();
            JsonNode groupData = entry.getValue();
            String prefix = generatePrefix(groupName);
            
            // 新格式: { type: "image", container: [...] }
            if (groupData.has("type") && groupData.has("container")) {
                String type = groupData.get("type").asText();
                if ("image".equals(type)) {
                    JsonNode container = groupData.get("container");
                    if (container.isArray()) {
                        for (JsonNode item : container) {
                            String icon = item.has("icon") ? item.get("icon").asText() : "";
                            String text = item.has("text") ? item.get("text").asText() : "";
                            
                            // 从 icon 中提取图片 URL
                            String url = extractImageUrl(icon);
                            if (!url.isEmpty() && !text.isEmpty()) {
                                String shortcode = ":" + prefix + "_" + text + ":";
                                map.put(shortcode, url);
                            }
                        }
                    }
                }
            } else {
                // 旧格式: { "name": "url" }
                groupData.fields().forEachRemaining(stickerEntry -> {
                    String name = stickerEntry.getKey();
                    String value = stickerEntry.getValue().asText("");
                    if (value.startsWith("http") || value.startsWith("/")) {
                        String shortcode = ":" + prefix + "_" + name + ":";
                        map.put(shortcode, value);
                    }
                });
            }
        });
        
        return map;
    }
    
    private String generatePrefix(String groupName) {
        return groupName.replaceAll("\\s+", "_");
    }

    
    private String extractImageUrl(String icon) {
        // 优先从 origin 属性提取原图 URL
        Pattern originPattern = Pattern.compile("origin=[\"']([^\"']+)[\"']");
        Matcher originMatcher = originPattern.matcher(icon);
        if (originMatcher.find()) {
            String url = originMatcher.group(1);
            if (url.startsWith("//")) {
                url = "https:" + url;
            }
            return url;
        }
        
        // 如果没有 origin，从 src 属性提取
        Pattern srcPattern = Pattern.compile("src=[\"']([^\"']+)[\"']");
        Matcher srcMatcher = srcPattern.matcher(icon);
        if (srcMatcher.find()) {
            String url = srcMatcher.group(1);
            if (url.startsWith("//")) {
                url = "https:" + url;
            }
            return url;
        }
        return "";
    }
    
    private String replaceShortcodes(String content, Map<String, String> stickerMap) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        Matcher matcher = SHORTCODE_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        
        while (matcher.find()) {
            // 去掉空格后的标准短代码格式
            String normalizedShortcode = ":" + matcher.group(1) + ":";
            String url = stickerMap.get(normalizedShortcode);
            
            String replacement;
            if (url != null) {
                String alt = matcher.group(1);
                String escapedUrl = escapeHtml(url);
                
                StringBuilder styleBuilder = new StringBuilder();
                styleBuilder.append("display:inline;vertical-align:middle;background:none;border:none;box-shadow:none;");
                
                if (cachedExtraStyle != null && !cachedExtraStyle.isEmpty()) {
                    styleBuilder.append(cachedExtraStyle);
                }
                
                String style = styleBuilder.toString();
                
                replacement = String.format(
                    "<img src=\"%s\" srcset=\"%s\" sizes=\"\" alt=\"%s\" title=\"%s\" class=\"sticker-emoji no-lightbox\" style=\"%s\" referrerpolicy=\"no-referrer\">",
                    escapedUrl, escapedUrl, escapeHtml(alt), escapeHtml(alt), style
                );
            } else {
                replacement = matcher.group(0);
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        // 处理转义的短代码：\:xxx\: -> :xxx:
        String processed = result.toString();
        processed = ESCAPED_SHORTCODE_PATTERN.matcher(processed).replaceAll(m -> {
            String name = m.group(1) != null ? m.group(1) : m.group(2);
            return ":" + name + ":";
        });
        
        return processed;
    }
    
    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
}
