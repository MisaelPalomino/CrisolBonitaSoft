package com.crisol.bonita.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.AMQP;

class PublicarConsultaInventarioConnectorTest {

    private TestConnector connector;

    @BeforeEach
    void setUp() {
        connector = new TestConnector();
        connector.setInputParameters(validParameters());
    }

    @Test
    void rechaza_un_parametro_obligatorio_ausente() {
        Map<String, Object> parameters = validParameters();
        parameters.put(PublicarConsultaInventarioConnector.RABBITMQ_HOST, null);
        TestConnector connectorSinHost = new TestConnector();
        connectorSinHost.setInputParameters(parameters);

        assertThrows(ConnectorValidationException.class, connectorSinHost::validateInputParameters);
    }

    @Test
    void rechaza_un_puerto_invalido() {
        Map<String, Object> parameters = validParameters();
        parameters.put(PublicarConsultaInventarioConnector.RABBITMQ_PORT, 70_000);
        connector.setInputParameters(parameters);

        assertThrows(ConnectorValidationException.class, connector::validateInputParameters);
    }

    @Test
    void publica_el_contrato_reconocido_por_django_y_genera_correlation_id() throws ConnectorException {
        Map<String, Object> outputs = connector.execute();

        assertThat(connector.queue).isEqualTo("crisol.inventario.request");
        assertThat(new String(connector.body, StandardCharsets.UTF_8))
                .isEqualTo("{\"tipo\":\"inventario.stock_bajo.consultar\"}");
        assertThat(connector.properties.getContentType()).isEqualTo("application/json");
        assertThat(connector.properties.getDeliveryMode()).isEqualTo(2);
        assertThat(connector.properties.getReplyTo()).isEqualTo("crisol.inventario.response");
        assertThat(connector.confirmed).isTrue();
        assertThat(outputs)
                .containsEntry(PublicarConsultaInventarioConnector.OUTPUT_MESSAGE_PUBLISHED, true)
                .containsEntry(
                        PublicarConsultaInventarioConnector.OUTPUT_CORRELATION_ID,
                        connector.properties.getCorrelationId());
        assertThat(connector.properties.getCorrelationId()).isNotBlank();
    }

    @Test
    void conserva_el_correlation_id_recibido() throws ConnectorException {
        Map<String, Object> parameters = validParameters();
        parameters.put(PublicarConsultaInventarioConnector.CORRELATION_ID, "gestion-42");
        connector.setInputParameters(parameters);

        Map<String, Object> outputs = connector.execute();

        assertThat(connector.properties.getCorrelationId()).isEqualTo("gestion-42");
        assertThat(outputs)
                .containsEntry(PublicarConsultaInventarioConnector.OUTPUT_CORRELATION_ID, "gestion-42");
    }

    private static Map<String, Object> validParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(PublicarConsultaInventarioConnector.RABBITMQ_HOST, "localhost");
        parameters.put(PublicarConsultaInventarioConnector.RABBITMQ_PORT, 5672);
        parameters.put(PublicarConsultaInventarioConnector.RABBITMQ_USERNAME, "guest");
        parameters.put(PublicarConsultaInventarioConnector.RABBITMQ_PASSWORD, "guest");
        parameters.put(PublicarConsultaInventarioConnector.RABBITMQ_VIRTUAL_HOST, "/");
        parameters.put(PublicarConsultaInventarioConnector.REQUEST_QUEUE, "crisol.inventario.request");
        parameters.put(PublicarConsultaInventarioConnector.RESPONSE_QUEUE, "crisol.inventario.response");
        parameters.put(
                PublicarConsultaInventarioConnector.MESSAGE_TYPE,
                "inventario.stock_bajo.consultar");
        return parameters;
    }

    private static final class TestConnector extends PublicarConsultaInventarioConnector {
        private String queue;
        private AMQP.BasicProperties properties;
        private byte[] body;
        private boolean confirmed;

        @Override
        protected void publishMessage(String queue, AMQP.BasicProperties properties, byte[] body)
                throws IOException {
            this.queue = queue;
            this.properties = properties;
            this.body = body;
        }

        @Override
        protected void waitForPublishConfirmation() throws IOException, InterruptedException, TimeoutException {
            confirmed = true;
        }
    }
}
