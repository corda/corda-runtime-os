package net.corda.libs.permission

import net.corda.data.permissions.User
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig

class PermissionServiceImpl(
        private val subscriptionFactory: SubscriptionFactory,
        private val processor: PermissionsTopicProcessor
) : PermissionService {

    companion object {
        private const val USER_PERMISSION_TOPIC = "user-permissions-topic"
        internal const val CONSUMER_GROUP = "PERMISSION_SERVICE"
    }

    @Volatile
    private var stopped = false

    private var subscription: CompactedSubscription<String, User>? = null

    override fun authorizeUser(requestId: String, loginName: String, permission: String): Boolean {

        val user = processor.getUser(loginName) ?: return false

        if (!user.enabled) return false

        return false

        // Review given new topics definition
        //val permissionRequested = PermissionUrl.fromUrl(permission).permissionRequested
        //return user.roleIds.flatMap { it.permissions }.any { it.type == PermissionType.ALLOW &&
        // it.permissionString == permissionRequested }
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
                                processor
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