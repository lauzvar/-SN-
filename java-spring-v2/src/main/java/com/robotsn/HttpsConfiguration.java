package com.robotsn;

import java.net.URI;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
public class HttpsConfiguration {
  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> redirectConnector(
      @Value("${valenbot.http-redirect-port:8082}") int httpPort,
      @Value("${server.port}") int httpsPort) {
    if (httpPort == httpsPort)
      throw new IllegalArgumentException("HTTP and HTTPS ports must differ");
    return factory -> {
      var connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
      connector.setPort(httpPort);
      connector.setScheme("http");
      connector.setSecure(false);
      connector.setRedirectPort(httpsPort);
      factory.addAdditionalTomcatConnectors(connector);
    };
  }

  @Bean
  FilterRegistrationBean<OncePerRequestFilter> httpsOnly(
      @Value("${valenbot.public-url}") String publicUrl) {
    URI origin = URI.create(publicUrl);
    if (!"https".equals(origin.getScheme())
        || origin.getHost() == null
        || origin.getRawUserInfo() != null
        || origin.getRawQuery() != null
        || origin.getRawFragment() != null
        || !(origin.getPath().isEmpty() || origin.getPath().equals("/")))
      throw new IllegalArgumentException("HTTPS requires a valid HTTPS public origin");
    String base = publicUrl.replaceAll("/+$", "");
    var filter =
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              jakarta.servlet.http.HttpServletRequest req,
              jakarta.servlet.http.HttpServletResponse res,
              jakarta.servlet.FilterChain chain)
              throws java.io.IOException, jakarta.servlet.ServletException {
            if (req.isSecure()) {
              chain.doFilter(req, res);
              return;
            }
            res.setHeader("Cache-Control", "no-store");
            if (!java.util.Set.of("GET", "HEAD").contains(req.getMethod())) {
              res.setStatus(400);
              res.setContentType("application/json;charset=UTF-8");
              res.getWriter().write("{\"message\":\"请通过 HTTPS 重新登录并提交操作\"}");
              return;
            }
            // Configured origin only; never trust Host or forwarded headers for redirects.
            res.setStatus(308);
            res.setHeader(
                "Location",
                base
                    + req.getRequestURI()
                    + (req.getQueryString() == null ? "" : "?" + req.getQueryString()));
          }
        };
    var bean = new FilterRegistrationBean<OncePerRequestFilter>(filter);
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return bean;
  }
}
