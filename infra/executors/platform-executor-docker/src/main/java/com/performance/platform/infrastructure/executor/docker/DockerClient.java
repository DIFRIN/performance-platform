package com.performance.platform.infrastructure.executor.docker;

import java.util.Map;

/**
 * Abstraction over Docker operations — start, stop, pull containers.
 * <p>
 * Package-private: only {@link DockerTaskExecutor} and {@link DefaultDockerClient}
 * within this package use it. External code should go through the TaskExecutor SPI.
 * <p>
 * A single method {@code runContainer} combines create + start because the two
 * operations are always used together in the performance-test lifecycle and
 * splitting them would require the executor to manage an intermediate "created"
 * state that has no operational value.
 */
interface DockerClient {

    /**
     * Pull a Docker image from the configured registry.
     *
     * @param image the image name (e.g. {@code nginx:latest})
     * @throws DockerException if the pull command fails
     */
    void pullImage(String image);

    /**
     * Create and start a container in one atomic operation ({@code docker run -d}).
     *
     * @param image         the image to run
     * @param containerName optional container name (may be {@code null})
     * @param ports         host-to-container port mappings (may be empty, never null)
     * @param env           environment variables (may be empty, never null)
     * @return the container ID
     * @throws DockerException if the operation fails
     */
    String runContainer(String image, String containerName,
                        Map<String, String> ports, Map<String, String> env);

    /**
     * Stop a running container ({@code docker stop}).
     *
     * @param containerId the container ID to stop
     * @throws DockerException if the stop command fails
     */
    void stopContainer(String containerId);

    /**
     * Check whether a container is currently running.
     *
     * @param containerId the container ID to check
     * @return {@code true} if the container status is "running"
     */
    boolean isRunning(String containerId);
}
