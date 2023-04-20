package net.corda.libs.external.messaging

import net.corda.libs.external.messaging.entities.InactiveResponseType

data class ExternalMessagingConfigDefaults(
    val receiveTopicPattern: String,
    val isActive: Boolean,
    val inactiveResponseType: InactiveResponseType
)
