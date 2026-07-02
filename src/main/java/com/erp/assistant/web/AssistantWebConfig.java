package com.erp.assistant.web;

import com.erp.assistant.application.AssistantProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Wiring for the assistant's SSE streaming. The chat endpoint offloads the blocking model call to this
 * dedicated executor so it never ties up the servlet request thread while a generation streams. Kept small
 * and separate from the request threads; the queue absorbs short bursts. Also binds
 * {@link AssistantProperties} ({@code app.assistant.*}).
 */
@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantWebConfig {

    @Bean
    Executor assistantSseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("assistant-sse-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.initialize();
        return executor;
    }
}
