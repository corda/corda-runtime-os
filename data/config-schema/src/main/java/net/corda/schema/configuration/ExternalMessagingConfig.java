package net.corda.schema.configuration;

@SuppressWarnings("unused")
public final class ExternalMessagingConfig {

    private ExternalMessagingConfig() {
    }

    public static final String ROUTE_DEFAULTS = "routeDefaults";

    public static final String EXTERNAL_MESSAGING_RECEIVE_TOPIC_PATTERN = ROUTE_DEFAULTS + ".receiveTopicPattern";

    public static final String EXTERNAL_MESSAGING_ACTIVE = ROUTE_DEFAULTS + ".active";

    public static final String EXTERNAL_MESSAGING_INTERACTIVE_RESPONSE_TYPE = ROUTE_DEFAULTS + ".inactiveResponseType";
}
