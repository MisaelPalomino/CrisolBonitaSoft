package com.crisol.bonita.connector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Publica en RabbitMQ la consulta de stock bajo solicitada por GestionInventario.
 */
public class PublicarConsultaInventarioConnector extends AbstractConnector {

    static final String RABBITMQ_HOST = "rabbitmqHost";
    static final String RABBITMQ_PORT = "rabbitmqPort";
    static final String RABBITMQ_USERNAME = "rabbitmqUsername";
    static final String RABBITMQ_PASSWORD = "rabbitmqPassword";
    static final String RABBITMQ_VIRTUAL_HOST = "rabbitmqVirtualHost";
    static final String REQUEST_QUEUE = "requestQueue";
    static final String RESPONSE_QUEUE = "responseQueue";
    static final String MESSAGE_TYPE = "messageType";
    static final String CORRELATION_ID = "correlationId";

    static final String OUTPUT_CORRELATION_ID = "correlationIdGenerado";
    static final String OUTPUT_MESSAGE_PUBLISHED = "mensajePublicado";

    private Connection connection;
    private Channel channel;

    @Override
    public void validateInputParameters() throws ConnectorValidationException {
        checkMandatoryStringInput(RABBITMQ_HOST);
        checkMandatoryStringInput(RABBITMQ_USERNAME);
        checkMandatoryStringInput(RABBITMQ_PASSWORD);
        checkMandatoryStringInput(RABBITMQ_VIRTUAL_HOST);
        checkMandatoryStringInput(REQUEST_QUEUE);
        checkMandatoryStringInput(RESPONSE_QUEUE);
        checkMandatoryStringInput(MESSAGE_TYPE);

        Object portValue = getInputParameter(RABBITMQ_PORT);
        if (!(portValue instanceof Integer port) || port < 1 || port > 65535) {
            throw new ConnectorValidationException(
                    this,
                    String.format("El parametro '%s' debe ser un Integer entre 1 y 65535.", RABBITMQ_PORT));
        }

        Object correlationId = getInputParameter(CORRELATION_ID);
        if (correlationId != null && !(correlationId instanceof String)) {
            throw new ConnectorValidationException(
                    this,
                    String.format("El parametro opcional '%s' debe ser String.", CORRELATION_ID));
        }
    }

    protected void checkMandatoryStringInput(String inputName) throws ConnectorValidationException {
        Object value = getInputParameter(inputName);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new ConnectorValidationException(
                    this,
                    String.format("Falta el parametro obligatorio '%s' o no es String.", inputName));
        }
    }

    @Override
    public void connect() throws ConnectorException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(input(RABBITMQ_HOST, String.class));
        factory.setPort(input(RABBITMQ_PORT, Integer.class));
        factory.setUsername(input(RABBITMQ_USERNAME, String.class));
        factory.setPassword(input(RABBITMQ_PASSWORD, String.class));
        factory.setVirtualHost(input(RABBITMQ_VIRTUAL_HOST, String.class));
        factory.setRequestedHeartbeat(60);
        factory.setConnectionTimeout(30_000);
        factory.setAutomaticRecoveryEnabled(true);

        try {
            connection = factory.newConnection("bonita:gestion-inventario");
            channel = connection.createChannel();
            channel.queueDeclare(input(REQUEST_QUEUE, String.class), true, false, false, null);
            channel.queueDeclare(input(RESPONSE_QUEUE, String.class), true, false, false, null);
            channel.confirmSelect();
        } catch (IOException | TimeoutException e) {
            throw new ConnectorException("No se pudo conectar con RabbitMQ.", e);
        }
    }

    @Override
    protected void executeBusinessLogic() throws ConnectorException {
        String correlationId = optionalTrimmedString(CORRELATION_ID);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        String requestQueue = input(REQUEST_QUEUE, String.class);
        String responseQueue = input(RESPONSE_QUEUE, String.class);
        String messageType = input(MESSAGE_TYPE, String.class);
        String body = "{\"tipo\":\"" + escapeJson(messageType) + "\"}";

        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .contentEncoding(StandardCharsets.UTF_8.name())
                .deliveryMode(2)
                .correlationId(correlationId)
                .replyTo(responseQueue)
                .build();

        try {
            publishMessage(requestQueue, properties, body.getBytes(StandardCharsets.UTF_8));
            waitForPublishConfirmation();
        } catch (IOException | InterruptedException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ConnectorException("No se pudo publicar la consulta de inventario.", e);
        }

        setOutputParameter(OUTPUT_CORRELATION_ID, correlationId);
        setOutputParameter(OUTPUT_MESSAGE_PUBLISHED, true);
    }

    protected void publishMessage(String queue, AMQP.BasicProperties properties, byte[] body) throws IOException {
        if (channel == null || !channel.isOpen()) {
            throw new IOException("El canal de RabbitMQ no esta abierto.");
        }
        channel.basicPublish("", queue, true, properties, body);
    }

    protected void waitForPublishConfirmation() throws IOException, InterruptedException, TimeoutException {
        if (channel == null || !channel.isOpen()) {
            throw new IOException("El canal de RabbitMQ no esta abierto.");
        }
        channel.waitForConfirmsOrDie(5_000);
    }

    @Override
    public void disconnect() throws ConnectorException {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (IOException | TimeoutException e) {
            throw new ConnectorException("No se pudo cerrar correctamente la conexion con RabbitMQ.", e);
        }
    }

    private String optionalTrimmedString(String inputName) {
        Object value = getInputParameter(inputName);
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            return null;
        }
        return stringValue.trim();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private <T> T input(String name, Class<T> type) throws ConnectorException {
        Object value = getInputParameter(name);
        if (!type.isInstance(value)) {
            throw new ConnectorException(
                    String.format("El parametro '%s' no tiene el tipo esperado %s.", name, type.getSimpleName()));
        }
        return type.cast(value);
    }
}
