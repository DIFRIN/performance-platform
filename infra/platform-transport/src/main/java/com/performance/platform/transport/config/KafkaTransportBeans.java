package com.performance.platform.transport.config;

import com.performance.platform.transport.kafka.KafkaExecutionTransport;
import com.performance.platform.transport.kafka.BundleSslEngineFactory;
import com.performance.platform.transport.tls.TransportTls;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.Map;
import java.util.HashMap;

/**
 * Beans Spring pour le transport Kafka.
 *
 * <p>Tous les beans sont qualifies avec le prefixe "transport" pour
 * eviter les collisions avec les beans PDR-020 ({@code KafkaClusterConfiguration})
 * et les eventuels beans auto-configures depuis {@code spring.kafka.*}.
 *
 * <p>Les beans sont actifs uniquement quand {@code transport.type=KAFKA}
 * (ou la variable d'environnement {@code TRANSPORT_TYPE=KAFKA}, ADR-006).
 */
@Configuration
@ConditionalOnProperty(name = "transport.type", havingValue = "KAFKA")
public class KafkaTransportBeans {

    private final ObjectProvider<TransportTls> tlsProvider;

    public KafkaTransportBeans(ObjectProvider<TransportTls> tlsProvider) {
        this.tlsProvider = tlsProvider;
    }

    private Map<String, Object> withTls(Map<String, Object> base) {
        var tls = tlsProvider.getIfAvailable(TransportTls::disabled);
        if (!tls.enabled()) {
            return base;
        }
        var config = new HashMap<>(base);
        config.put("security.protocol", "SSL");
        config.put("ssl.engine.factory.class", BundleSslEngineFactory.class);
        config.put(BundleSslEngineFactory.BUNDLE_CONFIG, tls.bundle());
        config.put("ssl.endpoint.identification.algorithm", "HTTPS");
        return config;
    }

    @Bean("transportProducerFactory")
    public ProducerFactory<String, byte[]> transportProducerFactory(
            KafkaTransportProperties props) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, props.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class,
                ProducerConfig.ACKS_CONFIG, props.producerAcks()
        );
        return new DefaultKafkaProducerFactory<>(withTls(config));
    }

    @Bean("transportKafkaTemplate")
    public KafkaTemplate<String, byte[]> transportKafkaTemplate(
            @Qualifier("transportProducerFactory")
            ProducerFactory<String, byte[]> transportProducerFactory) {
        return new KafkaTemplate<>(transportProducerFactory);
    }

    @Bean("transportConsumerFactory")
    public ConsumerFactory<String, byte[]> transportConsumerFactory(
            KafkaTransportProperties props) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, props.bootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"
        );
        return new DefaultKafkaConsumerFactory<>(withTls(config));
    }

    @Bean("transportContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, byte[]>
            transportContainerFactory(
                @Qualifier("transportConsumerFactory")
                ConsumerFactory<String, byte[]> transportConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(transportConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public KafkaExecutionTransport kafkaExecutionTransport(
            KafkaTransportProperties props,
            @Qualifier("transportKafkaTemplate") KafkaTemplate<String, byte[]> template,
            @Qualifier("transportContainerFactory")
            ConcurrentKafkaListenerContainerFactory<String, byte[]> containerFactory) {
        return new KafkaExecutionTransport(props, template, containerFactory);
    }
}
