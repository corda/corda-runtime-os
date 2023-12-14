package net.corda.rest

/**
 * [RestResource] is a marker interface to indicate a class that contains HTTP endpoints.
 * Any class that provides HTTP endpoints must implement
 * this class; using the annotations alone does not suffice.
 */
interface RestResource {

    /** Returns the HTTP Rest protocol version. Exists since version 0 so guaranteed to be present. */
    val protocolVersion: Int
}
