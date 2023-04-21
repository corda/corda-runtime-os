package net.corda.schema.cordapp.configuration;

/** The keys for various configurations for a worker. */
public final class ConfigKeys {

    // These root keys are the values that will be used when configuration changes. Writers will use them when
    // publishing changes to one of the config sections defined by a key, and readers will use the keys to
    // determine which config section a given update is for.
    public static final String EXTERNAL_MESSAGING_CONFIG = "corda.external.messaging";
}
