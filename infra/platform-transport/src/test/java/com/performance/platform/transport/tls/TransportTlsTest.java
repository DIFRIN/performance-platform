package com.performance.platform.transport.tls;

import com.performance.platform.transport.config.KafkaTransportBeans;
import com.performance.platform.transport.config.TransportConfiguration;
import com.performance.platform.transport.config.TransportTlsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransportTlsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TransportConfiguration.class, KafkaTransportBeans.class);

    @Test
    void transportTlsIsDisabledByDefaultWithoutSslBundles() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(TransportTls.class).enabled()).isFalse();
        });
    }

    @Test
    void mutualAuthenticationIsDefaultWhenTlsIsEnabled() {
        assertThat(new TransportTlsProperties(true, "agent", null).clientAuth())
                .isEqualTo(TransportTlsProperties.ClientAuth.NEED);
        assertThatThrownBy(() -> new TransportTlsProperties(true, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingBundleFailsStartupRatherThanFallingBackToPlaintext() {
        runner.withPropertyValues("transport.tls.enabled=true", "transport.tls.bundle=missing")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("transport.tls.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
