package net.corda.web.api

import java.util.concurrent.CompletableFuture

fun interface WebHandler {

    /**
     * Handle a function to be run on when a webserver endpoint is hit
     *
     * @param context an implementation of WebContext which contains the request and response as well as headers etc.
     * @return The same WebContext object, which has been updated by this function
     */
    fun handle(context: WebContext) : CompletableFuture<WebContext>
}