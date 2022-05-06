package com.example.websocket.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.websocket.server.PathParam;

/**
 * className: UserRegister
 * package:com.example.websocket.controller*Description:
 *
 * @Date:2022/2/5 21:01
 * @Author : 3167038449@qq.com
 */
@RestController
public class RegisterController {
    @Autowired
    JDBCController jdbcController;

    @RequestMapping("/reg/user/{name}/{password}")
    public String UserRegister(@PathVariable("name") String name, @PathVariable("password")String password){
        return jdbcController.UserRegister(name, password);
    }

    @GetMapping("/reg/dev/{name}/{password}")
    public String DevRegister(@PathVariable("name")String name, @PathVariable("password")String password){
        return jdbcController.DevsRegister(name, password);
    }

}
