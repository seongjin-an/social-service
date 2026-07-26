package com.social.message.config;

import com.social.message.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SnowflakeConfig {

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(
        @Value("${chatting.snowflake.worker-id:0}") long workerId
    ) {
        return new SnowflakeIdGenerator(workerId);
    }
}
