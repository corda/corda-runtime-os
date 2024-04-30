package net.corda.restclient.dto

import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.libs.external.messaging.entities.Route

/**
 * This is a duplicate of [net.corda.libs.external.messaging.entities.Routes]
 * Because we need to use the [CpiIdentifier2] type
 */
data class Routes(
    @JsonProperty("cpiIdentifier")
    val cpiIdentifier: CpiIdentifier2,
    @JsonProperty("routes")
    val routes: List<Route>
)