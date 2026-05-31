package com.cos.fairbid.common.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * SPA(React) 정적 서빙 + 클라이언트 라우팅 fallback 설정.
 *
 * 데모 배포에서 backend(Spring) 하나가 프론트 빌드 산출물(classpath:/static/)까지 서빙한다.
 * (Dockerfile 이 frontend 빌드 결과 dist 를 backend 의 static 리소스로 포함한다.)
 *
 * React Router 는 /auctions, /login 같은 경로를 클라이언트에서 처리하므로,
 * 서버에 해당 정적 파일이 없을 때 404 대신 index.html 을 돌려줘야 새로고침/직접 진입이 동작한다.
 * 단 실제 API(/api/**)·WebSocket(/ws/**)·정적 에셋(.js/.css 등 확장자 있는 요청)은 fallback 대상에서 제외한다.
 *
 * @Controller 가 아닌 WebMvcConfigurer 로 구현한 이유: 헥사고날 ArchUnit 규칙상
 * Controller 는 UseCase 를 통해야 하는데, 이 정적 fallback 은 비즈니스 로직이 없으므로 설정으로 처리한다.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);

                        // 1) 실제 정적 파일이 있으면 그대로 서빙 (index.html, /assets/*.js 등)
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }

                        // 2) API·WebSocket 경로는 fallback 하지 않는다 (각 컨트롤러/핸들러가 처리)
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws")) {
                            return null;
                        }

                        // 3) 확장자가 있는 요청(파일을 찾던 것)은 fallback 하지 않는다 → 정상 404
                        //    (예: 없는 이미지/폰트. index.html 로 받으면 깨진 응답이 됨)
                        if (resourcePath.contains(".")) {
                            return null;
                        }

                        // 4) 그 외(점 없는 SPA 라우트)는 index.html 로 위임 → React Router 가 처리
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
