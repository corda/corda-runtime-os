package net.corda.web.api

interface WebHandler {
    fun handle(context: WebContext) : WebContext
}