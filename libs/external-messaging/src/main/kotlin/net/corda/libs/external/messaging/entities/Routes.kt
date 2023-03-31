package net.corda.libs.external.messaging.entities

import com.fasterxml.jackson.annotation.JsonProperty
import net.corda.libs.packaging.core.CpiIdentifier

data  class Routes(
    @JsonProperty("cpiIdentifier")
    val cpiIdentifier:CpiIdentifier,
    @JsonProperty("routes")
    val routes:List<Route>
)

