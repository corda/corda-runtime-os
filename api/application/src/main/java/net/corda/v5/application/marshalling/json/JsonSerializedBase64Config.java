package net.corda.v5.application.marshalling.json;

/**
 * When objects are serialized to Json using custom serializers, and they require to be encoded into base64 encoded, this
 * enum can be used to configure how that base64 is written into the Json.
 */
public enum JsonSerializedBase64Config {
    /**
     * Standard base64 encoding.
     */
    MIME,

    /**
     * Standard base64 encoding but with no line feeds.
     */
    MIME_NO_LINEFEEDS,

    /**
     * Base64 encoding suitable for URLs. No line feeds, and characters that need quoting in URLs are replaced with ones
     * that don't.
     */
    MODIFIED_FOR_URL,

    /**
     * Standard base64 encoding but with shorter line lengths.
     */
    PEM
}
