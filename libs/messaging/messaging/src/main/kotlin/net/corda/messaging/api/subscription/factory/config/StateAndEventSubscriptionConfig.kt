package net.corda.messaging.api.subscription.factory.config

/**
 * Class to store the required params to create a StateAndEventSubscription.
 * @property groupName The unique ID for a group of consumers.
 * @property instanceId Required for transactional publishing. Id must be unique across worker instances
 * and subscription instances within the same consumer group.
 * @property stateTopic Topic on which states will be available
 * @property eventTopic Topic on which events will arrive from.
 */
data class StateAndEventSubscriptionConfig(
    val groupName: String,
    val instanceId: Int,
    val stateTopic: String,
    val eventTopic: String,
)
