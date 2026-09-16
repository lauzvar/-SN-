package com.robotsn;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {
  @GetMapping({"/", "/r/{sn}"})
  String index() {
    return "index";
  }

  @GetMapping("/favicon.ico")
  @org.springframework.web.bind.annotation.ResponseBody
  org.springframework.http.ResponseEntity<Void> favicon() {
    return org.springframework.http.ResponseEntity.noContent().build();
  }

  @GetMapping("/login")
  String login() {
    return "login";
  }
}
