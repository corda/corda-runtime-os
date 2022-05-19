package com.example.securitymanager.one.flows

import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.net.HttpURLConnection
import java.net.URL


@Component
class HttpRequestFlow
@Activate constructor() : Flow<Int> {

    override fun call(): Int  {
        var url = URL("http://example.com")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.connect()
        return connection.responseCode
    }
}

