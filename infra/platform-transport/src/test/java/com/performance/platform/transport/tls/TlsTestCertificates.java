package com.performance.platform.transport.tls;

import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.jks.JksSslStoreBundle;
import org.springframework.boot.ssl.jks.JksSslStoreDetails;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TlsTestCertificates {

    public static final Path DIRECTORY = generate();

    private TlsTestCertificates() {
    }

    private static Path generate() {
        try {
            Path root = Path.of("").toAbsolutePath();
            while (!Files.exists(root.resolve("distribution/example/tls/generate-test-certificates.sh"))) {
                root = root.getParent();
                if (root == null) {
                    throw new IllegalStateException("Cannot locate TLS certificate generator");
                }
            }
            Path parent = Path.of("target").toAbsolutePath();
            Files.createDirectories(parent);
            Path directory = Files.createTempDirectory(parent, "tls-tests-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try (var paths = Files.walk(directory)) {
                    for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException("Cannot clean TLS test certificates", e);
                }
            }, "cleanup-tls-test-certificates"));
            Path output = directory.resolve("certificates");
            Path log = directory.resolve("openssl.log");
            var builder = new ProcessBuilder("sh",
                    root.resolve("distribution/example/tls/generate-test-certificates.sh").toString(),
                    output.toString()).redirectErrorStream(true).redirectOutput(log.toFile());
            builder.environment().put("TLS_TEST_STORE_PASSWORD", "changeit");
            var process = builder.start();
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("TLS test certificate generation timed out");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("TLS test certificate generation failed: " + Files.readString(log));
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot generate TLS test certificates", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TLS certificate generation interrupted", e);
        }
    }

    public static SslBundle pem(String identity) {
        var trust = PemSslStoreDetails.forCertificate(DIRECTORY.resolve("ca.crt").toUri().toString());
        var key = identity == null ? null : PemSslStoreDetails
                .forCertificate(DIRECTORY.resolve(identity + ".crt").toUri().toString())
                .withPrivateKey(DIRECTORY.resolve(identity + ".key").toUri().toString());
        return SslBundle.of(new PemSslStoreBundle(key, trust));
    }

    public static SslBundle pkcs12(String identity) {
        var key = new JksSslStoreDetails("PKCS12", null,
                DIRECTORY.resolve(identity + ".p12").toUri().toString(), "changeit");
        var trust = new JksSslStoreDetails("PKCS12", null,
                DIRECTORY.resolve("truststore.p12").toUri().toString(), "changeit");
        return SslBundle.of(new JksSslStoreBundle(key, trust));
    }
}
