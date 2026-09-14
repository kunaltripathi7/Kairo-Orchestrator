package dev.kunal.kairo.api.config;

import java.util.Map;
import java.util.concurrent.Executor;

import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    @Bean("outboxExecutor")
    public Executor outboxExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator()); // injects context snapshot mdc and stuff to every async thread originated from this exectuor.
        executor.initialize();
        return executor;
    }
}
