package net.corda.p2p.app.simulator

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object TimeValidator {
    fun validate() {
        val logger = LoggerFactory.getLogger(TimeValidator::class.java)
        val json = ObjectMapper()
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder(URI.create("http://worldtimeapi.org/api/timezone/Europe/London")).build()
        val reply = json.readValue(
            client.send(request, HttpResponse.BodyHandlers.ofString()).body(),
            Map::class.java
        )
        logger.info("Actual time is $reply")
        logger.info("System.currentTimeMillis() / 1000 = ${System.currentTimeMillis() / 1000}")
        logger.info("world clock unix time = ${reply["unixtime"]}")
    }
}