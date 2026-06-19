package com.performance.platform.injection.gatling.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProtocolSupportInfo")
class ProtocolSupportInfoTest {

    @Test
    @DisplayName("should declare HTTP as supported")
    void shouldSupportHttp() {
        assertThat(ProtocolSupportInfo.isSupported("HTTP")).isTrue();
    }

    @Test
    @DisplayName("should declare HTTPS as supported")
    void shouldSupportHttps() {
        assertThat(ProtocolSupportInfo.isSupported("HTTPS")).isTrue();
    }

    @Test
    @DisplayName("should declare WebSocket as supported")
    void shouldSupportWebSocket() {
        assertThat(ProtocolSupportInfo.isSupported("WebSocket")).isTrue();
    }

    @Test
    @DisplayName("should declare Kafka as supported")
    void shouldSupportKafka() {
        assertThat(ProtocolSupportInfo.isSupported("Kafka")).isTrue();
    }

    @Test
    @DisplayName("should declare JMS as supported")
    void shouldSupportJms() {
        assertThat(ProtocolSupportInfo.isSupported("JMS")).isTrue();
    }

    @Test
    @DisplayName("should NOT declare gRPC as supported")
    void shouldNotSupportGrpc() {
        assertThat(ProtocolSupportInfo.isSupported("gRPC")).isFalse();
    }

    @Test
    @DisplayName("should explicitly exclude gRPC")
    void shouldExcludeGrpc() {
        assertThat(ProtocolSupportInfo.isExcluded("gRPC")).isTrue();
    }

    @Test
    @DisplayName("should return correct protocol count")
    void shouldReturnCorrectCount() {
        assertThat(ProtocolSupportInfo.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("should return ordered set of supported protocols")
    void shouldReturnOrderedSet() {
        Set<String> protocols = ProtocolSupportInfo.supportedProtocols();

        assertThat(protocols)
                .containsExactly("HTTP", "HTTPS", "WebSocket", "Kafka", "JMS");
    }

    @Test
    @DisplayName("should return false for unknown protocol")
    void shouldReturnFalseForUnknownProtocol() {
        assertThat(ProtocolSupportInfo.isSupported("FTP")).isFalse();
    }

    @Test
    @DisplayName("should return false for excluded check on supported protocol")
    void shouldNotExcludeSupportedProtocols() {
        for (String protocol : ProtocolSupportInfo.SUPPORTED_PROTOCOLS) {
            assertThat(ProtocolSupportInfo.isExcluded(protocol))
                    .as("Protocol " + protocol + " should not be excluded")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("should be a utility class (private constructor)")
    void shouldBeUtilityClass() {
        assertThat(ProtocolSupportInfo.class.getDeclaredConstructors())
                .allSatisfy(c -> assertThat(c.canAccess(null)).isFalse());
    }
}
