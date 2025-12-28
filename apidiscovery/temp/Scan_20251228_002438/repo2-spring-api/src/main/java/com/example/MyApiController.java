package com.example;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
public class MyApiController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello World";
    }
}
