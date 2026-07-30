package com.crisol.bonita.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsumirRespuestaInventarioConnectorTest {

    private TestConnector connector;

    @BeforeEach
    void setUp() {
        connector = new TestConnector();
        connector.setInputParameters(validParameters());
    }

    @Test
    void rechaza_correlation_id_ausente() {
        Map<String, Object> parameters = validParameters();
        parameters.put(ConsumirRespuestaInventarioConnector.CORRELATION_ID, null);
        connector.setInputParameters(parameters);

        assertThrows(ConnectorValidationException.class, connector::validateInputParameters);
    }

    @Test
    void consume_la_respuesta_correspondiente_y_extrae_stock_bajo() throws ConnectorException {
        connector.messages.add(message(10, "caso-42", true));

        Map<String, Object> outputs = connector.execute();

        assertThat(outputs)
                .containsEntry(ConsumirRespuestaInventarioConnector.OUTPUT_STOCK_BAJO, true)
                .containsKey(ConsumirRespuestaInventarioConnector.OUTPUT_RESPONSE_JSON);
        assertThat(connector.acknowledged).containsExactly(10L);
        assertThat(connector.requeued).isEmpty();
        assertThat(connector.rejected).isEmpty();
    }

    @Test
    void conserva_respuestas_de_otros_casos_en_la_cola() throws ConnectorException {
        connector.messages.add(message(11, "otro-caso", false));
        connector.messages.add(message(12, "caso-42", false));

        Map<String, Object> outputs = connector.execute();

        assertThat(outputs)
                .containsEntry(ConsumirRespuestaInventarioConnector.OUTPUT_STOCK_BAJO, false);
        assertThat(connector.acknowledged).containsExactly(12L);
        assertThat(connector.requeued).containsExactly(11L);
    }

    @Test
    void rechaza_una_respuesta_correlacionada_con_tipo_invalido() {
        String invalidBody = "{\"tipo\":\"desconocido\",\"datos\":{\"alerta\":true}}";
        connector.messages.add(new ConsumirRespuestaInventarioConnector.ReceivedMessage(
                13,
                "caso-42",
                invalidBody.getBytes(StandardCharsets.UTF_8)));

        assertThrows(ConnectorException.class, connector::execute);
        assertThat(connector.rejected).containsExactly(13L);
    }

    private static Map<String, Object> validParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ConsumirRespuestaInventarioConnector.RABBITMQ_HOST, "localhost");
        parameters.put(ConsumirRespuestaInventarioConnector.RABBITMQ_PORT, 5672);
        parameters.put(ConsumirRespuestaInventarioConnector.RABBITMQ_USERNAME, "guest");
        parameters.put(ConsumirRespuestaInventarioConnector.RABBITMQ_PASSWORD, "guest");
        parameters.put(ConsumirRespuestaInventarioConnector.RABBITMQ_VIRTUAL_HOST, "/");
        parameters.put(
                ConsumirRespuestaInventarioConnector.RESPONSE_QUEUE,
                "crisol.inventario.response");
        parameters.put(ConsumirRespuestaInventarioConnector.CORRELATION_ID, "caso-42");
        parameters.put(ConsumirRespuestaInventarioConnector.TIMEOUT_SECONDS, 1);
        return parameters;
    }

    private static ConsumirRespuestaInventarioConnector.ReceivedMessage message(
            long deliveryTag,
            String correlationId,
            boolean stockBajo) {
        String body = String.format(
                "{\"tipo\":\"inventario.stock_bajo.respuesta\",\"datos\":{\"alerta\":%s}}",
                stockBajo);
        return new ConsumirRespuestaInventarioConnector.ReceivedMessage(
                deliveryTag,
                correlationId,
                body.getBytes(StandardCharsets.UTF_8));
    }

    private static final class TestConnector extends ConsumirRespuestaInventarioConnector {
        private final Deque<ReceivedMessage> messages = new ArrayDeque<>();
        private final List<Long> acknowledged = new ArrayList<>();
        private final List<Long> requeued = new ArrayList<>();
        private final List<Long> rejected = new ArrayList<>();

        @Override
        protected ReceivedMessage getMessage(String queue) {
            return messages.pollFirst();
        }

        @Override
        protected void acknowledgeMessage(long deliveryTag) {
            acknowledged.add(deliveryTag);
        }

        @Override
        protected void requeueMessage(long deliveryTag) {
            requeued.add(deliveryTag);
        }

        @Override
        protected void rejectMessage(long deliveryTag) {
            rejected.add(deliveryTag);
        }
    }
}
