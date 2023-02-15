package net.corda.v5.application.marshalling.json;

/**
 * Possible types of nodes in Json.
 */
public enum JsonNodeReaderType {
    /**
     * An array of objects, values, or other arrays.
     */
    ARRAY,

    /**
     * Boolean, either true or false.
     */
    BOOLEAN,

    /**
     * The Json representation of "null".
     */
    NULL,

    /**
     * A number, a superset of all number types, integer, floating point, double, long etc.
     */
    NUMBER,

    /**
     * A Json object.
     */
    OBJECT,

    /**
     * Text.
     */
    STRING
}
