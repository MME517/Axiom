package com.workhub.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares all RabbitMQ topology:
 * <ul>
 *   <li>Dead-Letter Exchange (DLX) + Dead-Letter Queue (DLQ)</li>
 *   <li>Main work exchange + queue bound with DLX arguments</li>
 * </ul>
 *
 * Retry strategy is configured in application.yml via
 * spring.rabbitmq.listener.simple.retry.*.
 * After max-attempts the message is nacked without requeue and RabbitMQ
 * routes it to the DLX → DLQ automatically.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${workhub.rabbitmq.report-queue}")
    private String reportQueue;

    @Value("${workhub.rabbitmq.report-exchange}")
    private String reportExchange;

    @Value("${workhub.rabbitmq.report-routing-key}")
    private String reportRoutingKey;

    @Value("${workhub.rabbitmq.dlq-name}")
    private String dlqName;

    @Value("${workhub.rabbitmq.dlx-name}")
    private String dlxName;

    // ── Dead-Letter infrastructure ─────────────────────────────────────────

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlxName, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(reportQueue);   // DLX routing key = original queue name
    }

    // ── Main work exchange + queue ─────────────────────────────────────────

    @Bean
    public DirectExchange reportExchange() {
        return new DirectExchange(reportExchange, true, false);
    }

    @Bean
    public Queue reportQueue() {
        return QueueBuilder.durable(reportQueue)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", reportQueue)
                .build();
    }

    @Bean
    public Binding reportBinding() {
        return BindingBuilder.bind(reportQueue())
                .to(reportExchange())
                .with(reportRoutingKey);
    }

    // ── Serialisation ──────────────────────────────────────────────────────

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }
}
