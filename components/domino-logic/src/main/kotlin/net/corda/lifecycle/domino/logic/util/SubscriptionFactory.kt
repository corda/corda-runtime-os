package net.corda.lifecycle.domino.logic.util

import net.corda.messaging.api.subscription.SubscriptionBase
import net.corda.messaging.api.subscription.config.SubscriptionConfig

interface SubscriptionFactory {

    fun subscriptonGenerator() :SubscriptionBase

    val subscriptionConfig: SubscriptionConfig
}