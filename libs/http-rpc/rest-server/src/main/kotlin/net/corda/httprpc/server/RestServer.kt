package net.corda.httprpc.server

import net.corda.lifecycle.Resource

interface RestServer : Resource {
    fun start()

    val port: Int
}
