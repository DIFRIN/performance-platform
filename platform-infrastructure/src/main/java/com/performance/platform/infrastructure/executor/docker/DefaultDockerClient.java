package com.performance.platform.infrastructure.executor.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Docker client implementation that delegates to the {@code docker} CLI.
 * <p>
 * Uses {@link ProcessBuilder} for every operation — consistent with the
 * {@code ShellTaskExecutor} pattern. No third-party Docker library is required.
 * <p>
 * Timeout for each CLI call is 60 seconds by default, which is generous
 * enough for image pulls on a reasonable connection while still providing
 * a safety net against hung processes.
 */
@Component
class DefaultDockerClient implements DockerClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultDockerClient.class);

    private static final long CLI_TIMEOUT_SECONDS = 60;

    @Override
    public void pullImage(String image) {
        log.info("action=docker_pull_start image={}", image);
        List<String> cmd = List.of("docker", "pull", image);
        String output = execute(cmd);
        log.info("action=docker_pull_complete image={} output={}", image, output.trim());
    }

    @Override
    public String runContainer(String image, String containerName,
                               Map<String, String> ports, Map<String, String> env) {
        var cmd = new ArrayList<String>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d"); // detached
        if (containerName != null && !containerName.isBlank()) {
            cmd.add("--name");
            cmd.add(containerName);
        }
        for (var entry : ports.entrySet()) {
            cmd.add("-p");
            cmd.add(entry.getKey() + ":" + entry.getValue());
        }
        for (var entry : env.entrySet()) {
            cmd.add("-e");
            cmd.add(entry.getKey() + "=" + entry.getValue());
        }
        cmd.add(image);

        log.info("action=docker_run_start image={} containerName={}", image, containerName);
        String containerId = execute(cmd).trim();
        if (containerId.isEmpty()) {
            throw new DockerException("docker run returned empty container ID");
        }
        log.info("action=docker_run_complete containerId={} image={}", containerId, image);
        return containerId;
    }

    @Override
    public void stopContainer(String containerId) {
        log.info("action=docker_stop_start containerId={}", containerId);
        List<String> cmd = List.of("docker", "stop", containerId);
        String output = execute(cmd);
        log.info("action=docker_stop_complete containerId={} output={}", containerId, output.trim());
    }

    @Override
    public boolean isRunning(String containerId) {
        List<String> cmd = List.of("docker", "inspect",
                "--format={{.State.Status}}", containerId);
        try {
            String status = execute(cmd).trim();
            return "running".equals(status);
        } catch (DockerException e) {
            // Container not found or inspect failed → not running
            log.debug("action=docker_inspect_failed containerId={}", containerId, e);
            return false;
        }
    }

    // ── CLI execution ──────────────────────────────────────────────────────

    private String execute(List<String> command) {
        try {
            var pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean completed = process.waitFor(CLI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new DockerException("docker CLI timed out: " + String.join(" ", command));
            }
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new DockerException(
                        "docker CLI exit code " + exitCode + ": " + output.trim());
            }
            return output;
        } catch (IOException e) {
            throw new DockerException("Failed to execute docker CLI: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerException("Interrupted while waiting for docker CLI", e);
        }
    }
}
