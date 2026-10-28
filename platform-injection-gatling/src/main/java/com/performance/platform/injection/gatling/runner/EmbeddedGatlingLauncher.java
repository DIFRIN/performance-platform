package com.performance.platform.injection.gatling.runner;

import akka.actor.ActorSystem;
import io.gatling.app.RunResult;
import io.gatling.app.RunResultProcessor;
import io.gatling.app.Runner;
import io.gatling.app.cli.GatlingArgsParser;
import io.gatling.app.cli.StatusCode;
import io.gatling.core.cli.GatlingArgs;
import io.gatling.core.config.GatlingConfiguration;
import io.gatling.netty.util.Transports;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.util.Either;
import scala.util.Left;
import scala.util.Right;

import java.nio.file.FileSystems;
import java.util.concurrent.TimeUnit;

/**
 * Lanceur Gatling embarque qui reproduit {@code io.gatling.app.Gatling$.start}
 * (Gatling 3.11.2) sans son bloc {@code finally} qui appelle
 * {@code LoggerFactory.getILoggerFactory().stop()}.
 * <p>
 * {@code Gatling$.fromArgs} est concu pour un process dedie : en fin de run il
 * arrete le {@code LoggerContext} Logback global (reset = appenders detaches),
 * ce qui coupe tous les logs de l'application hote apres la simulation.
 * Ici le cycle de vie est identique (parsing CLI, ActorSystem, EventLoopGroup,
 * {@code Runner}, terminaison bornee par {@code gatling.core.shutdownTimeout},
 * {@code RunResultProcessor} pour le code retour) mais le logging de l'hote
 * n'est jamais touche. A re-valider a chaque montee de version Gatling
 * ({@code Runner}/{@code RunResultProcessor} sont internes a {@code gatling-app}).
 */
final class EmbeddedGatlingLauncher {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedGatlingLauncher.class);

    private EmbeddedGatlingLauncher() {
    }

    static int launch(String[] args) {
        Either<GatlingArgs, StatusCode> parsed = new GatlingArgsParser(args).parseArguments();
        if (parsed instanceof Right<GatlingArgs, StatusCode> invalid) {
            return invalid.value().code();
        }
        return start(((Left<GatlingArgs, StatusCode>) parsed).value());
    }

    private static int start(GatlingArgs gatlingArgs) {
        // Same workaround as Gatling for https://github.com/gatling/gatling/issues/3411
        FileSystems.getDefault();
        GatlingConfiguration configuration = GatlingConfiguration.load();
        RunResult runResult = gatlingArgs.reportsOnly().isDefined()
                ? new RunResult(gatlingArgs.reportsOnly().get(), true)
                : runSimulation(gatlingArgs, configuration);
        return new RunResultProcessor(gatlingArgs, configuration).processRunResult(runResult).code();
    }

    private static RunResult runSimulation(GatlingArgs gatlingArgs, GatlingConfiguration configuration) {
        ActorSystem system = ActorSystem.create("GatlingSystem", GatlingConfiguration.loadActorSystemConfiguration());
        EventLoopGroup eventLoopGroup = Transports.newEventLoopGroup(
                configuration.netty().useNativeTransport(), configuration.netty().useIoUring(), 0, "gatling");
        long shutdownTimeoutMs = configuration.core().shutdownTimeout();
        try {
            return Runner.apply(system, eventLoopGroup, gatlingArgs, configuration).run();
        } catch (Throwable e) {
            log.error("action=gatling_run_crashed", e);
            throw e;
        } finally {
            terminateActorSystem(system, shutdownTimeoutMs);
            eventLoopGroup.shutdownGracefully(0, shutdownTimeoutMs, TimeUnit.MILLISECONDS);
        }
    }

    private static void terminateActorSystem(ActorSystem system, long timeoutMs) {
        try {
            system.terminate();
            system.getWhenTerminated().toCompletableFuture().get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("action=gatling_actor_system_terminate_interrupted", e);
        } catch (Exception e) {
            log.debug("action=gatling_actor_system_terminate_failed", e);
        }
    }
}
