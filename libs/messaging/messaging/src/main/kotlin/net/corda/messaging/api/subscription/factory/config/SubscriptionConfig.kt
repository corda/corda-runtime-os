package net.corda.messaging.api.subscription.factory.config

/**
 * Class to store the required params to create a Subscription.
 */
data class SubscriptionConfig (val groupName:String,
                               val instanceId:Int,
                               val eventTopic:String)