package com.performance.platform.transport.http;

import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.TransportException;
import com.performance.platform.transport.message.ExecutionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Point d'entree HTTP pour les events remontes par les agents.
 * <p>
 * Les agents POSTent leurs {@link ExecutionEvent} vers cet endpoint.
 * Le controller les transmet au transport actif via
 * {@link ExecutionTransport#publishEvent(ExecutionEvent)}, qui les
 * dispatche aux souscripteurs enregistres (orchestrateur).
 * <p>
 * Actif uniquement lorsque {@code transport.type=HTTP}.
 */
@RestController
@ConditionalOnProperty(name = "transport.type", havingValue = "HTTP")
public class HttpEventCallbackController {

    private static final Logger log = LoggerFactory.getLogger(HttpEventCallbackController.class);

    private final ExecutionTransport transport;

    public HttpEventCallbackController(ExecutionTransport transport) {
        this.transport = transport;
    }

    /**
     * Recoit un evenement d'execution emis par un agent.
     * <p>
     * Le corps de la requete est deserialise en {@link ExecutionEvent}
     * et transmis aux souscripteurs locaux via {@code publishEvent}.
     *
     * @param event l'evenement recu
     * @return 202 Accepted si l'evenement est accepte
     */
    @PostMapping("/api/v1/events")
    public ResponseEntity<Void> onExecutionEvent(@RequestBody ExecutionEvent event) {
        log.debug("action=receive_event eventId={} executionId={} agentId={} type={}",
                event.id().value(),
                event.executionId().value(),
                event.agentId() != null ? event.agentId().value() : "null",
                event.eventType());
        try {
            transport.publishEvent(event);
        } catch (TransportException e) {
            log.warn("action=publish_event_rejected eventId={} error={}",
                    event.id().value(), e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.accepted().build();
    }
}
