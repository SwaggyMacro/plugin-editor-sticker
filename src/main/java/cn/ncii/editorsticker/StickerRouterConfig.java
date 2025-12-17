package cn.ncii.editorsticker;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
@RequiredArgsConstructor
public class StickerRouterConfig {

    private final StickerEndpoint stickerEndpoint;

    @Bean
    public RouterFunction<ServerResponse> stickerRouterFunction() {
        return stickerEndpoint.endpoint();
    }
}
