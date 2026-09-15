package com.robotsn;
import java.io.IOException;
import java.util.List;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.filter.OncePerRequestFilter;

/** Apply disabled accounts, role changes and password resets to existing sessions. */
public class AccountStateFilter extends OncePerRequestFilter {
  private final JdbcTemplate db;
  public AccountStateFilter(JdbcTemplate db){this.db=db;}
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
    var auth=SecurityContextHolder.getContext().getAuthentication();
    if(auth!=null && !(auth instanceof AnonymousAuthenticationToken)){
      var rows=db.queryForList("SELECT active,role,password_hash,auth_version FROM sys_user WHERE username=?",auth.getName());
      var session=req.getSession(false); String key="accountPasswordHash";
      boolean stale=!rows.isEmpty()&&session!=null&&session.getAttribute(key)!=null&&!session.getAttribute(key).equals(rows.get(0).get("password_hash"));
      if(rows.isEmpty()||!Boolean.TRUE.equals(rows.get(0).get("active"))||stale||(session!=null&&session.getAttribute("authVersion")!=null&&!session.getAttribute("authVersion").equals(rows.get(0).get("auth_version")))){
        if(session!=null)session.invalidate();SecurityContextHolder.clearContext();
        if(req.getRequestURI().startsWith("/api/")){res.setStatus(401);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\"账号状态已变更，请重新登录\"}");}
        else res.sendRedirect("/login");return;
      }
      if(session!=null){session.setAttribute(key,rows.get(0).get("password_hash"));session.setAttribute("authVersion",rows.get(0).get("auth_version"));}
      var updated=new UsernamePasswordAuthenticationToken(auth.getPrincipal(),auth.getCredentials(),List.of(new SimpleGrantedAuthority("ROLE_"+rows.get(0).get("role"))));
      updated.setDetails(auth.getDetails());SecurityContextHolder.getContext().setAuthentication(updated);
    }
    chain.doFilter(req,res);
  }
}
