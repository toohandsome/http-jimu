package com.example.demo;


import com.jimu.http.service.HttpJimuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    HttpJimuService httpJimuService;

    @PostMapping("/call/{httpId}")
    public String callHttp(@PathVariable("httpId") String httpId, @RequestBody Map<String, Object> params) {
        try {
            return httpJimuService.call(httpId, params);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @GetMapping("/hello")
    public String hello() {
        return "Test Demo is running!";
    }
}
