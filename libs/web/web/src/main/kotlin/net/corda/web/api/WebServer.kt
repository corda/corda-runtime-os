package net.corda.web.api

/**
 * The WebServer interface represents a web server that can handle HTTP requests and responses.
 * It provides methods to start and stop the server, register and remove endpoints, and get the server's port.
 */
interface WebServer{

    /**
     * Port the server will listen on
     */
    val port: Int?

    /**
     * Start the webserver
     *
     * @param port the port for the server to listen on
     */
    fun start(port: Int)

    /**
     * Stop the webserver
     */
    fun stop()

    /**
     * Register an endpoint
     *
     * @param endpoint The Endpoint to be registered on the webserver, containing a handler to be ran when
     * the endpoint is hit
     */
    fun registerEndpoint(endpoint: Endpoint)

    /**
     * Remove an endpoint
     *
     * @param endpoint The Endpoint to be removed from the webserver, meaning it will no longer be reachable
     */
    fun removeEndpoint(endpoint: Endpoint)
}