package net.corda.external.messaging.entities

import net.corda.libs.external.messaging.entities.Route

data class VerifiedRoute (
    val route: Route,
    val externalReceiveTopicNameExists:Boolean
)
