package com.example.websocket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * @author buhao
 * @version WebSocketConfig.java, v 0.1 2019-10-18 15:45 buhao
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig {

    //如果打包发布时候注释掉下面的内容
    @Bean
    public ServerEndpointExporter serverEndpoint() {
        return new ServerEndpointExporter();
    }
}
