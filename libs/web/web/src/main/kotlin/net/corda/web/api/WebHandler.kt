package net.corda.web.api

fun interface WebHandler {

    /**
     * Handle a function to be run on when an webserver endpoint is hit
     *
     * @param context an implementation of WebContext which contains the request and response as well as headers etc.
     * @return The same WebContext object, which has been updated by this function
     */
    fun handle(context: WebContext) : WebContext
}