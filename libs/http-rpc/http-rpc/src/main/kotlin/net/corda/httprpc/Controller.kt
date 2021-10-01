package net.corda.v5.httprpc.api

import io.javalin.apibuilder.EndpointGroup

interface Controller {

    fun register()
}