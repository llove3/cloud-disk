package com.example.clouddisk.ai;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiRabbitConfig {
    public static final String EXCHANGE = "cloud.disk.ai";
    public static final String QUEUE = "cloud.disk.ai.index";
    public static final String DEAD_EXCHANGE = "cloud.disk.ai.dead";
    public static final String DEAD_QUEUE = "cloud.disk.ai.dead.queue";

    @Bean DirectExchange aiExchange() { return new DirectExchange(EXCHANGE, true, false); }
    @Bean DirectExchange aiDeadExchange() { return new DirectExchange(DEAD_EXCHANGE, true, false); }
    @Bean Queue aiQueue() {
        return QueueBuilder.durable(QUEUE).withArgument("x-dead-letter-exchange", DEAD_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "dead").build();
    }
    @Bean Queue aiDeadQueue() { return QueueBuilder.durable(DEAD_QUEUE).build(); }
    @Bean Binding aiBinding() { return BindingBuilder.bind(aiQueue()).to(aiExchange()).with("index"); }
    @Bean Binding aiDeadBinding() { return BindingBuilder.bind(aiDeadQueue()).to(aiDeadExchange()).with("dead"); }
}
