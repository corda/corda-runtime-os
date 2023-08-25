package net.corda.web.server

import net.corda.messaging.api.WebContext

interface WebHandler {
    fun handle(context: WebContext) : WebContext
}