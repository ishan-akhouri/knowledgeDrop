package dev.coms4156.knowledgedrop.config;

import dev.coms4156.knowledgedrop.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web configuration: puts client-token authentication in front of every protected endpoint. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AuthInterceptor authInterceptor;

  /**
   * Creates the configuration.
   *
   * @param authInterceptor interceptor that validates the client token
   */
  public WebConfig(AuthInterceptor authInterceptor) {
    this.authInterceptor = authInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // Every endpoint except registerClient and health requires a client token.
    registry
        .addInterceptor(authInterceptor)
        .addPathPatterns("/knowledgeDrop/**")
        .excludePathPatterns("/knowledgeDrop/registerClient", "/knowledgeDrop/health");
  }
}
