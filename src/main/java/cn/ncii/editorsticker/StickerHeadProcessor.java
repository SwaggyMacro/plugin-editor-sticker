package cn.ncii.editorsticker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.SettingFetcher;
import run.halo.app.theme.dialect.TemplateHeadProcessor;

/**
 * 注入表情包样式和脚本
 * 使用静态文件 + 时间戳避免缓存问题
 */
@Component
@RequiredArgsConstructor
public class StickerHeadProcessor implements TemplateHeadProcessor {

    private final SettingFetcher settingFetcher;

    // 配置变更时间戳，用于缓存控制
    private volatile long configTimestamp = System.currentTimeMillis();

    @Override
    public Mono<Void> process(ITemplateContext context, IModel model,
                              IElementModelStructureHandler structureHandler) {
        return Mono.fromRunnable(() -> {
            // 获取配置，使用默认值
            String inlineMaxWidth = "64px";
            String inlineMaxHeight = "64px";
            String soloMaxWidth = "256px";
            String soloMaxHeight = "256px";
            String stickerStyle = "";

            try {
                var setting = settingFetcher.get("basic");
                if (setting != null) {
                    if (setting.has("inlineMaxWidth")) {
                        inlineMaxWidth = setting.get("inlineMaxWidth").asText("64px");
                    }
                    if (setting.has("inlineMaxHeight")) {
                        inlineMaxHeight = setting.get("inlineMaxHeight").asText("64px");
                    }
                    if (setting.has("soloMaxWidth")) {
                        soloMaxWidth = setting.get("soloMaxWidth").asText("256px");
                    }
                    if (setting.has("soloMaxHeight")) {
                        soloMaxHeight = setting.get("soloMaxHeight").asText("256px");
                    }
                    if (setting.has("stickerStyle")) {
                        stickerStyle = setting.get("stickerStyle").asText("");
                    }
                }
            } catch (Exception e) {
                // 使用默认值
            }

            IModelFactory modelFactory = context.getModelFactory();

            // 使用时间戳作为缓存控制参数
            long timestamp = configTimestamp;

            // 注入 CSS 变量
            String cssVars = generateCssVars(
                inlineMaxWidth, inlineMaxHeight,
                soloMaxWidth, soloMaxHeight,
                stickerStyle
            );
            model.add(modelFactory.createText(cssVars));

            // 引用静态 CSS 文件
            String cssLink = String.format(
                "<link rel=\"stylesheet\" href=\"/plugins/editor-sticker/assets/static/sticker.css?v=%d\">\n",
                timestamp
            );
            model.add(modelFactory.createText(cssLink));

            // 引用静态 JS 文件
            String jsScript = String.format(
                "<script src=\"/plugins/editor-sticker/assets/static/sticker.js?v=%d\"></script>\n",
                timestamp
            );
            model.add(modelFactory.createText(jsScript));
        });
    }

    /**
     * 生成 CSS 变量，用于动态配置
     */
    private String generateCssVars(String inlineMaxWidth, String inlineMaxHeight,
                                   String soloMaxWidth, String soloMaxHeight,
                                   String stickerStyle) {
        StringBuilder sb = new StringBuilder();
        sb.append("<style id=\"sticker-vars\">\n");
        sb.append(":root {\n");
        sb.append("    --sticker-inline-max-width: ").append(inlineMaxWidth).append(";\n");
        sb.append("    --sticker-inline-max-height: ").append(inlineMaxHeight).append(";\n");
        sb.append("    --sticker-solo-max-width: ").append(soloMaxWidth).append(";\n");
        sb.append("    --sticker-solo-max-height: ").append(soloMaxHeight).append(";\n");
        sb.append("}\n");

        // 添加用户自定义样式
        if (stickerStyle != null && !stickerStyle.isEmpty()) {
            sb.append("/* 用户自定义样式 */\n");
            sb.append(stickerStyle).append("\n");
        }

        sb.append("</style>\n");
        return sb.toString();
    }

    /**
     * 更新配置时间戳，触发缓存刷新
     */
    public void refreshTimestamp() {
        this.configTimestamp = System.currentTimeMillis();
    }
}
