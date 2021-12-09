package net.corda.cli.application.services

import net.corda.cli.api.services.HttpService

class HttpRpcService : HttpService {

    override fun get() : String {
        return "Sending Get Request"
    }

    override fun put() : String {
        return "Sending Put Request"
    }

    override fun patch() : String {
        return "Sending Patch Request"
    }

    override fun post() : String {
        return "Sending Post Request"
    }

    override fun delete() : String {
        return "Sending Delete Request"
    }
}

