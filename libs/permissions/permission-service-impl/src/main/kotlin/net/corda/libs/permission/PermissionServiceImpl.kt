package net.corda.libs.permission

import com.typesafe.config.Config
import net.corda.data.permissions.User
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import java.time.Instant

class PermissionServiceImpl(
        private val subscriptionFactory: SubscriptionFactory,
        private val boostrapConfig: Config,
        private val processor: PermissionsTopicProcessor
) : PermissionService {

    companion object {
        private const val USER_PERMISSION_TOPIC = "user-permissions-topic"
        internal const val CONSUMER_GROUP = "PERMISSION_SERVICE"
    }

    @Volatile
    private var stopped = false

    private var subscription: CompactedSubscription<String, User>? = null

    override fun authorizeUser(requestId: String, requestUrl: String, loginName: String, timeoutTimestamp: Instant): Boolean {

        val user = processor.getUser(loginName) ?: return false

        //Return true for now if the user exist in the local Map
        return true
    }

    override val isRunning: Boolean
        get() = !stopped

    override fun start() {
            if (subscription == null) {
                subscription =
                        subscriptionFactory.createCompactedSubscription(
                                SubscriptionConfig(
                                        CONSUMER_GROUP,
                                        USER_PERMISSION_TOPIC
                                ),
                                processor,
                                boostrapConfig
                        )
                subscription!!.start()
                stopped = false
            }
    }

    override fun stop() {
            if (!stopped) {
                subscription?.stop()
                subscription = null
                stopped = true
            }
    }
}