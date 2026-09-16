package com.robotsn;

import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService users(DataSource ds) {
    var m = new JdbcUserDetailsManager(ds);
    m.setUsersByUsernameQuery(
        "select username,password_hash,active from sys_user where username=?");
    m.setAuthoritiesByUsernameQuery(
        "select username,'ROLE_'||role from sys_user where username=? and active");
    return m;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, JdbcTemplate db) throws Exception {
    var success = new SavedRequestAwareAuthenticationSuccessHandler();
    success.setDefaultTargetUrl("/");
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginPage("/login")
                    .successHandler(
                        (req, res, auth) -> {
                          db.update(
                              "insert into login_log(username,success) values (?,true)",
                              auth.getName());
                          req.getSession()
                              .setAttribute(
                                  "accountPasswordHash",
                                  db.queryForObject(
                                      "select password_hash from sys_user where username=?",
                                      String.class,
                                      auth.getName()));
                          req.getSession()
                              .setAttribute(
                                  "authVersion",
                                  db.queryForObject(
                                      "select auth_version from sys_user where username=?",
                                      Long.class,
                                      auth.getName()));
                          success.onAuthenticationSuccess(req, res, auth);
                        })
                    .failureHandler(
                        (req, res, ex) -> {
                          String u = req.getParameter("username");
                          db.update(
                              "insert into login_log(username,success) values (?,false)",
                              u == null ? "" : u.substring(0, Math.min(u.length(), 64)));
                          res.sendRedirect("/login?error");
                        })
                    .permitAll())
        .logout(l -> l.logoutSuccessUrl("/login?logout"))
        .exceptionHandling(
            e ->
                e.defaultAuthenticationEntryPointFor(
                    (req, res, ex) -> {
                      res.setStatus(401);
                      res.setContentType("application/json;charset=UTF-8");
                      res.getWriter().write("{\"message\":\"请先登录\"}");
                    },
                    req -> req.getRequestURI().startsWith("/api/")))
        .addFilterAfter(
            new AccountStateFilter(db),
            org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class);
    return http.build();
  }
}
