package net.corda.cli.api.services

import net.corda.cli.api.CordaCliService

class HttpRpcService {

    fun sendRequest(type: HttpType, request: String, url: String): String {
        return "Sent \"${request}\" to \"${url}\". Type: ${type.name}"
    }
}

enum class HttpType {
    GET,
    PUT,
    PATCH,
    DELETE,
    UPDATE
}