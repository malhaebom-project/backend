package com.malhaebom.malhaebom.infra.openapi;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SwaggerUiThemeConfiguration {
    private static final String THEME_STYLESHEET = "/swagger-ui-assets/malhaebom-theme.css";
	private static final String FAVICON = "/swagger-ui-assets/malhaebom-mark.svg";

	@Bean
	public SwaggerIndexTransformer malhaebomSwaggerIndexTransformer(
		SwaggerUiConfigProperties swaggerUiConfig,
		SwaggerUiOAuthProperties swaggerUiOAuthProperties,
		SwaggerWelcomeCommon swaggerWelcomeCommon,
		ObjectMapperProvider objectMapperProvider
	) {
		return new MalhaebomSwaggerIndexTransformer(
			swaggerUiConfig,
			swaggerUiOAuthProperties,
			swaggerWelcomeCommon,
			objectMapperProvider
		);
	}

	private static class MalhaebomSwaggerIndexTransformer extends SwaggerIndexPageTransformer {
		private MalhaebomSwaggerIndexTransformer(
			SwaggerUiConfigProperties swaggerUiConfig,
			SwaggerUiOAuthProperties swaggerUiOAuthProperties,
			SwaggerWelcomeCommon swaggerWelcomeCommon,
			ObjectMapperProvider objectMapperProvider
		) {
			super(swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
		}

		@Override
		public Resource transform(
			HttpServletRequest request,
			Resource resource,
			ResourceTransformerChain transformerChain
		) throws IOException {
			Resource transformed = super.transform(request, resource, transformerChain);
			if (!"index.html".equals(resource.getFilename())) {
				return transformed;
			}

			String html = new String(transformed.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
				.replace("<title>Swagger UI</title>", "<title>말해봄 API 문서</title>")
				.replace(
					"<link rel=\"icon\" type=\"image/png\" href=\"./favicon-32x32.png\" sizes=\"32x32\" />",
					"<link rel=\"icon\" type=\"image/svg+xml\" href=\"" + FAVICON + "\">"
				)
				.replace(
					"<link rel=\"icon\" type=\"image/png\" href=\"./favicon-16x16.png\" sizes=\"16x16\" />",
					""
				)
				.replace(
					"</head>",
					"<link rel=\"stylesheet\" href=\"" + THEME_STYLESHEET + "\">"
						+ "</head>"
				);
			return new TransformedResource(transformed, html.getBytes(StandardCharsets.UTF_8));
		}
	}
}
