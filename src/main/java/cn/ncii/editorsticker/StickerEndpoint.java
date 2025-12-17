package cn.ncii.editorsticker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * 自定义表情包管理 API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StickerEndpoint {

    private final ReactiveExtensionClient client;
    private final ReactiveSettingFetcher settingFetcher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String CUSTOM_STICKERS_CONFIGMAP = "editor-sticker-custom-data";

    public RouterFunction<ServerResponse> endpoint() {
        return route(GET("/apis/editor-sticker.ncii.cn/v1alpha1/custom-stickers"), this::getCustomStickers)
            .andRoute(POST("/apis/editor-sticker.ncii.cn/v1alpha1/custom-stickers"), this::saveCustomStickers)
            .andRoute(POST("/apis/editor-sticker.ncii.cn/v1alpha1/custom-stickers/group"), this::addGroup)
            .andRoute(DELETE("/apis/editor-sticker.ncii.cn/v1alpha1/custom-stickers/group/{name}"), this::deleteGroup)
            .andRoute(POST("/apis/editor-sticker.ncii.cn/v1alpha1/custom-stickers/group/{name}/sticker"), this::addSticker)
            .andRoute(DELETE("/apis/editor-sticker.ncii.cn/v1alpha1/custom-stickers/group/{groupName}/sticker/{stickerText}"), this::deleteSticker)
            // 静态资源路由
            .andRoute(GET("/plugins/editor-sticker/assets/static/sticker.css"), this::serveCss)
            .andRoute(GET("/plugins/editor-sticker/assets/static/sticker.js"), this::serveJs);
    }
    
    /**
     * 提供 CSS 静态文件
     */
    private Mono<ServerResponse> serveCss(ServerRequest request) {
        return Mono.fromCallable(() -> readResourceAsString("static/sticker.css"))
            .flatMap(content -> ServerResponse.ok()
                .contentType(MediaType.valueOf("text/css; charset=utf-8"))
                .bodyValue(content))
            .onErrorResume(e -> {
                log.error("Failed to read sticker.css", e);
                return ServerResponse.notFound().build();
            });
    }
    
    /**
     * 提供 JS 静态文件
     */
    private Mono<ServerResponse> serveJs(ServerRequest request) {
        return Mono.fromCallable(() -> readResourceAsString("static/sticker.js"))
            .flatMap(content -> ServerResponse.ok()
                .contentType(MediaType.valueOf("application/javascript; charset=utf-8"))
                .bodyValue(content))
            .onErrorResume(e -> {
                log.error("Failed to read sticker.js", e);
                return ServerResponse.notFound().build();
            });
    }
    
    /**
     * 使用当前类的类加载器读取资源文件
     */
    private String readResourceAsString(String resourcePath) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }
    
    /**
     * 获取自定义表情数据
     */
    private Mono<ServerResponse> getCustomStickers(ServerRequest request) {
        return client.fetch(ConfigMap.class, CUSTOM_STICKERS_CONFIGMAP)
            .map(configMap -> {
                String data = configMap.getData().get("stickers");
                if (data == null || data.isEmpty()) {
                    return "{}";
                }
                return data;
            })
            .defaultIfEmpty("{}")
            .flatMap(data -> ServerResponse.ok().bodyValue(data));
    }
    
    /**
     * 保存完整的自定义表情数据
     */
    private Mono<ServerResponse> saveCustomStickers(ServerRequest request) {
        return request.bodyToMono(String.class)
            .flatMap(body -> {
                // 验证 JSON 格式
                try {
                    objectMapper.readTree(body);
                } catch (JsonProcessingException e) {
                    return ServerResponse.badRequest().bodyValue("Invalid JSON format");
                }
                
                return client.fetch(ConfigMap.class, CUSTOM_STICKERS_CONFIGMAP)
                    .flatMap(configMap -> {
                        configMap.getData().put("stickers", body);
                        return client.update(configMap)
                            .flatMap(saved -> ServerResponse.ok().bodyValue("{\"success\":true}"));
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        ConfigMap configMap = new ConfigMap();
                        run.halo.app.extension.Metadata metadata = new run.halo.app.extension.Metadata();
                        metadata.setName(CUSTOM_STICKERS_CONFIGMAP);
                        configMap.setMetadata(metadata);
                        configMap.setData(new java.util.HashMap<>());
                        configMap.getData().put("stickers", body);
                        return client.create(configMap)
                            .flatMap(saved -> ServerResponse.ok().bodyValue("{\"success\":true}"));
                    }));
            });
    }

    
    /**
     * 添加分组
     */
    private Mono<ServerResponse> addGroup(ServerRequest request) {
        return request.bodyToMono(JsonNode.class)
            .flatMap(body -> {
                String groupName = body.get("name").asText();
                String type = body.has("type") ? body.get("type").asText() : "image";
                
                return getOrCreateConfigMap()
                    .flatMap(configMap -> {
                        try {
                            String data = configMap.getData().getOrDefault("stickers", "{}");
                            ObjectNode root = (ObjectNode) objectMapper.readTree(data);
                            
                            if (root.has(groupName)) {
                                return ServerResponse.badRequest().bodyValue("{\"error\":\"Group already exists\"}");
                            }
                            
                            ObjectNode group = objectMapper.createObjectNode();
                            group.put("type", type);
                            group.set("container", objectMapper.createArrayNode());
                            root.set(groupName, group);
                            
                            configMap.getData().put("stickers", objectMapper.writeValueAsString(root));
                            return client.update(configMap)
                                .flatMap(saved -> ServerResponse.ok().bodyValue("{\"success\":true}"));
                        } catch (Exception e) {
                            log.error("Failed to add group", e);
                            return ServerResponse.badRequest().bodyValue("{\"error\":\"" + e.getMessage() + "\"}");
                        }
                    });
            });
    }
    
    /**
     * 删除分组
     */
    private Mono<ServerResponse> deleteGroup(ServerRequest request) {
        String groupName = request.pathVariable("name");
        
        return getOrCreateConfigMap()
            .flatMap(configMap -> {
                try {
                    String data = configMap.getData().getOrDefault("stickers", "{}");
                    ObjectNode root = (ObjectNode) objectMapper.readTree(data);
                    
                    if (!root.has(groupName)) {
                        return ServerResponse.notFound().build();
                    }
                    
                    root.remove(groupName);
                    configMap.getData().put("stickers", objectMapper.writeValueAsString(root));
                    return client.update(configMap)
                        .flatMap(saved -> ServerResponse.ok().bodyValue("{\"success\":true}"));
                } catch (Exception e) {
                    log.error("Failed to delete group", e);
                    return ServerResponse.badRequest().bodyValue("{\"error\":\"" + e.getMessage() + "\"}");
                }
            });
    }
    
    /**
     * 添加表情到分组
     */
    private Mono<ServerResponse> addSticker(ServerRequest request) {
        String groupName = request.pathVariable("name");
        
        return request.bodyToMono(JsonNode.class)
            .flatMap(body -> {
                String text = body.get("text").asText();
                String icon = body.get("icon").asText();
                
                return getOrCreateConfigMap()
                    .flatMap(configMap -> {
                        try {
                            String data = configMap.getData().getOrDefault("stickers", "{}");
                            ObjectNode root = (ObjectNode) objectMapper.readTree(data);
                            
                            if (!root.has(groupName)) {
                                return ServerResponse.notFound().build();
                            }
                            
                            ObjectNode group = (ObjectNode) root.get(groupName);
                            ArrayNode container = (ArrayNode) group.get("container");
                            
                            ObjectNode sticker = objectMapper.createObjectNode();
                            sticker.put("text", text);
                            sticker.put("icon", icon);
                            container.add(sticker);
                            
                            configMap.getData().put("stickers", objectMapper.writeValueAsString(root));
                            return client.update(configMap)
                                .flatMap(saved -> ServerResponse.ok().bodyValue("{\"success\":true}"));
                        } catch (Exception e) {
                            log.error("Failed to add sticker", e);
                            return ServerResponse.badRequest().bodyValue("{\"error\":\"" + e.getMessage() + "\"}");
                        }
                    });
            });
    }
    
    /**
     * 从分组删除表情
     */
    private Mono<ServerResponse> deleteSticker(ServerRequest request) {
        String groupName = request.pathVariable("groupName");
        String stickerText = request.pathVariable("stickerText");
        
        return getOrCreateConfigMap()
            .flatMap(configMap -> {
                try {
                    String data = configMap.getData().getOrDefault("stickers", "{}");
                    ObjectNode root = (ObjectNode) objectMapper.readTree(data);
                    
                    if (!root.has(groupName)) {
                        return ServerResponse.notFound().build();
                    }
                    
                    ObjectNode group = (ObjectNode) root.get(groupName);
                    ArrayNode container = (ArrayNode) group.get("container");
                    
                    for (int i = 0; i < container.size(); i++) {
                        if (container.get(i).get("text").asText().equals(stickerText)) {
                            container.remove(i);
                            break;
                        }
                    }
                    
                    configMap.getData().put("stickers", objectMapper.writeValueAsString(root));
                    return client.update(configMap)
                        .flatMap(saved -> ServerResponse.ok().bodyValue("{\"success\":true}"));
                } catch (Exception e) {
                    log.error("Failed to delete sticker", e);
                    return ServerResponse.badRequest().bodyValue("{\"error\":\"" + e.getMessage() + "\"}");
                }
            });
    }
    
    private Mono<ConfigMap> getOrCreateConfigMap() {
        return client.fetch(ConfigMap.class, CUSTOM_STICKERS_CONFIGMAP)
            .switchIfEmpty(Mono.defer(() -> {
                ConfigMap configMap = new ConfigMap();
                run.halo.app.extension.Metadata metadata = new run.halo.app.extension.Metadata();
                metadata.setName(CUSTOM_STICKERS_CONFIGMAP);
                configMap.setMetadata(metadata);
                configMap.setData(new java.util.HashMap<>());
                configMap.getData().put("stickers", "{}");
                return client.create(configMap);
            }));
    }
}
