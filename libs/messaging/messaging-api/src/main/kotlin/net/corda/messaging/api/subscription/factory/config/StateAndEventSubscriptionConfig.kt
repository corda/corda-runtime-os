package net.corda.messaging.api.subscription.factory.config

/**
 * Class to store the required params to create a StateAndEventSubscription.
 */
data class StateAndEventSubscriptionConfig (val groupName:String,
                               val instanceId:Int,
                               val stateTopic:String,
                               val eventTopic:String)