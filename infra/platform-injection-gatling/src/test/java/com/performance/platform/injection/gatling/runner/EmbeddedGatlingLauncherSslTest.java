package com.performance.platform.injection.gatling.runner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmbeddedGatlingLauncher — SSL provider fallback")
class EmbeddedGatlingLauncherSslTest {

    @Test
    @DisplayName("keeps BoringSSL when it is available")
    void keepsOpenSslWhenAvailable() {
        assertThat(EmbeddedGatlingLauncher.shouldFallbackToJdkSsl(true, null)).isFalse();
    }

    @Test
    @DisplayName("falls back to JDK SSL when BoringSSL is unavailable and unset")
    void fallsBackWhenUnavailable() {
        assertThat(EmbeddedGatlingLauncher.shouldFallbackToJdkSsl(false, null)).isTrue();
    }

    @Test
    @DisplayName("honours an explicit useOpenSsl setting")
    void honoursExplicitSetting() {
        assertThat(EmbeddedGatlingLauncher.shouldFallbackToJdkSsl(false, "true")).isFalse();
        assertThat(EmbeddedGatlingLauncher.shouldFallbackToJdkSsl(false, "false")).isFalse();
    }
}
