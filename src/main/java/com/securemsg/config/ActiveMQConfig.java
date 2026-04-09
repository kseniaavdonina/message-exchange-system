package com.securemsg.config;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import javax.jms.Session;

/**
 * Конфигурация ActiveMQ для JMS (Java Message Service).
 * Настраивает:
 * <ul>
 *     <li>Подключение к брокеру ActiveMQ</li>
 *     <li>JmsTemplate для отправки сообщений</li>
 *     <li>Фабрику слушателей для приёма сообщений</li>
 *     <li>Конвертер сообщений в JSON</li>
 * </ul>
 */
@Configuration
@EnableJms
public class ActiveMQConfig {

    private static final Logger log = LoggerFactory.getLogger(ActiveMQConfig.class);

    private static final String BROKER_URL = "tcp://activemq:61616";
    private static final String BROKER_USERNAME = "admin";
    private static final String BROKER_PASSWORD = "admin";

    /**
     * Создаёт фабрику соединений с ActiveMQ.
     * Настраивает URL брокера, учётные данные и доверие ко всем пакетам.
     *
     * @return ActiveMQConnectionFactory
     */
    @Bean
    public ActiveMQConnectionFactory activeMQConnectionFactory() {
        log.info("Creating ActiveMQ connection factory: {}", BROKER_URL);

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
        factory.setBrokerURL(BROKER_URL);
        factory.setUserName(BROKER_USERNAME);
        factory.setPassword(BROKER_PASSWORD);
        factory.setTrustAllPackages(true);

        log.debug("ActiveMQ connection factory configured");
        return factory;
    }

    /**
     * Создаёт JmsTemplate для отправки сообщений.
     * Использует JSON конвертер и режим подтверждения CLIENT_ACKNOWLEDGE.
     *
     * @return JmsTemplate
     */
    @Bean
    public JmsTemplate jmsTemplate() {
        log.debug("Creating JmsTemplate");

        JmsTemplate jmsTemplate = new JmsTemplate(activeMQConnectionFactory());
        jmsTemplate.setMessageConverter(jacksonJmsMessageConverter());
        jmsTemplate.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        return jmsTemplate;
    }

    /**
     * Создаёт фабрику слушателей для асинхронного приёма сообщений.
     * Использует JSON конвертер, режим CLIENT_ACKNOWLEDGE и 1 поток на слушателя.
     *
     * @return DefaultJmsListenerContainerFactory
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory() {
        log.debug("Creating JMS listener container factory");

        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(activeMQConnectionFactory());
        factory.setMessageConverter(jacksonJmsMessageConverter());
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE);
        factory.setConcurrency("1");
        return factory;
    }

    /**
     * Создаёт конвертер сообщений в формат JSON.
     * Устанавливает тип сообщения TEXT и свойство _type для десериализации.
     *
     * @return MessageConverter для JSON
     */
    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        log.debug("Creating Jackson JSON message converter");

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }
}