package com.crisol.bonita.connector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

/**
 * Consume la respuesta de inventario que corresponde al correlation_id del caso.
 */
public class ConsumirRespuestaInventarioConnector extends AbstractConnector {

    static final String RABBITMQ_HOST = "rabbitmqHost";
    static final String RABBITMQ_PORT = "rabbitmqPort";
    static final String RABBITMQ_USERNAME = "rabbitmqUsername";
    static final String RABBITMQ_PASSWORD = "rabbitmqPassword";
    static final String RABBITMQ_VIRTUAL_HOST = "rabbitmqVirtualHost";
    static final String RESPONSE_QUEUE = "responseQueue";
    static final String CORRELATION_ID = "correlationId";
    static final String TIMEOUT_SECONDS = "timeoutSeconds";

    static final String OUTPUT_STOCK_BAJO = "stockBajo";
    static final String OUTPUT_RESPONSE_JSON = "respuestaJson";

    private static final String RESPONSE_TYPE = "inventario.stock_bajo.respuesta";
    private static final Pattern TYPE_PATTERN =
            Pattern.compile("\"tipo\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ALERT_PATTERN =
            Pattern.compile("\"alerta\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);

    private Connection connection;
    private Channel channel;

    @Override
    public void validateInputParameters() throws ConnectorValidationException {
        checkMandatoryStringInput(RABBITMQ_HOST);
        checkMandatoryStringInput(RABBITMQ_USERNAME);
        checkMandatoryStringInput(RABBITMQ_PASSWORD);
        checkMandatoryStringInput(RABBITMQ_VIRTUAL_HOST);
        checkMandatoryStringInput(RESPONSE_QUEUE);
        checkMandatoryStringInput(CORRELATION_ID);

        Object portValue = getInputParameter(RABBITMQ_PORT);
        if (!(portValue instanceof Integer port) || port < 1 || port > 65535) {
            throw new ConnectorValidationException(
                    this,
                    String.format("El parametro '%s' debe ser un Integer entre 1 y 65535.", RABBITMQ_PORT));
        }

        Object timeoutValue = getInputParameter(TIMEOUT_SECONDS);
        if (!(timeoutValue instanceof Integer timeout) || timeout < 1 || timeout > 300) {
            throw new ConnectorValidationException(
                    this,
                    String.format("El parametro '%s' debe ser un Integer entre 1 y 300.", TIMEOUT_SECONDS));
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
            connection = factory.newConnection("bonita:respuesta-gestion-inventario");
            channel = connection.createChannel();
            channel.queueDeclare(input(RESPONSE_QUEUE, String.class), true, false, false, null);
        } catch (IOException | TimeoutException e) {
            throw new ConnectorException("No se pudo conectar con RabbitMQ.", e);
        }
    }

    @Override
    protected void executeBusinessLogic() throws ConnectorException {
        String responseQueue = input(RESPONSE_QUEUE, String.class);
        String expectedCorrelationId = input(CORRELATION_ID, String.class).trim();
        int timeoutSeconds = input(TIMEOUT_SECONDS, Integer.class);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        List<Long> unmatchedDeliveryTags = new ArrayList<>();

        try {
            while (System.nanoTime() < deadline) {
                ReceivedMessage message = getMessage(responseQueue);
                if (message == null) {
                    Thread.sleep(200);
                    continue;
                }

                if (!expectedCorrelationId.equals(message.correlationId())) {
                    unmatchedDeliveryTags.add(message.deliveryTag());
                    continue;
                }

                String responseJson = new String(message.body(), StandardCharsets.UTF_8);
                boolean stockBajo;
                try {
                    stockBajo = readStockAlert(responseJson);
                } catch (ConnectorException error) {
                    rejectMessage(message.deliveryTag());
                    releaseUnmatched(unmatchedDeliveryTags);
                    throw error;
                }

                acknowledgeMessage(message.deliveryTag());
                releaseUnmatched(unmatchedDeliveryTags);
                setOutputParameter(OUTPUT_STOCK_BAJO, stockBajo);
                setOutputParameter(OUTPUT_RESPONSE_JSON, responseJson);
                return;
            }

            releaseUnmatched(unmatchedDeliveryTags);
            throw new ConnectorException(
                    String.format(
                            "No se recibio una respuesta para correlation_id '%s' en %s segundos.",
                            expectedCorrelationId,
                            timeoutSeconds));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            safelyRelease(unmatchedDeliveryTags, e);
            throw new ConnectorException("La espera de la respuesta de inventario fue interrumpida.", e);
        } catch (IOException e) {
            safelyRelease(unmatchedDeliveryTags, e);
            throw new ConnectorException("No se pudo consumir la respuesta de inventario.", e);
        }
    }

    protected ReceivedMessage getMessage(String queue) throws IOException {
        if (channel == null || !channel.isOpen()) {
            throw new IOException("El canal de RabbitMQ no esta abierto.");
        }
        GetResponse response = channel.basicGet(queue, false);
        if (response == null) {
            return null;
        }
        return new ReceivedMessage(
                response.getEnvelope().getDeliveryTag(),
                response.getProps().getCorrelationId(),
                response.getBody());
    }

    protected void acknowledgeMessage(long deliveryTag) throws IOException {
        channel.basicAck(deliveryTag, false);
    }

    protected void requeueMessage(long deliveryTag) throws IOException {
        channel.basicNack(deliveryTag, false, true);
    }

    protected void rejectMessage(long deliveryTag) throws IOException {
        channel.basicNack(deliveryTag, false, false);
    }

    private void releaseUnmatched(List<Long> deliveryTags) throws IOException {
        for (long deliveryTag : deliveryTags) {
            requeueMessage(deliveryTag);
        }
        deliveryTags.clear();
    }

    private void safelyRelease(List<Long> deliveryTags, Exception originalError) {
        try {
            releaseUnmatched(deliveryTags);
        } catch (IOException releaseError) {
            originalError.addSuppressed(releaseError);
        }
    }

    private boolean readStockAlert(String responseJson) throws ConnectorException {
        Matcher typeMatcher = TYPE_PATTERN.matcher(responseJson);
        if (!typeMatcher.find() || !RESPONSE_TYPE.equals(typeMatcher.group(1))) {
            throw new ConnectorException("La respuesta tiene un tipo de mensaje desconocido.");
        }

        Matcher alertMatcher = ALERT_PATTERN.matcher(responseJson);
        if (!alertMatcher.find()) {
            throw new ConnectorException("La respuesta no contiene el campo booleano datos.alerta.");
        }
        return Boolean.parseBoolean(alertMatcher.group(1));
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

    private <T> T input(String name, Class<T> type) throws ConnectorException {
        Object value = getInputParameter(name);
        if (!type.isInstance(value)) {
            throw new ConnectorException(
                    String.format("El parametro '%s' no tiene el tipo esperado %s.", name, type.getSimpleName()));
        }
        return type.cast(value);
    }

    protected record ReceivedMessage(long deliveryTag, String correlationId, byte[] body) {
    }
}
