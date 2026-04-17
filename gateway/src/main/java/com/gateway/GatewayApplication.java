package com.gateway;

import com.gateway.config.GatewayProperties;
import com.gateway.config.JwtProperties;
import com.gateway.config.RateLimitingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({GatewayProperties.class, JwtProperties.class, RateLimitingProperties.class})
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

}
