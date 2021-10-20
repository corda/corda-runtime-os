package net.corda.libs.permission

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.data.config.Configuration
import net.corda.data.permissions.User
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import java.time.Instant
import java.util.Collections
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.log

class PermissionServiceImpl(
        private val subscriptionFactory: SubscriptionFactory,
        private val boostrapConfig: Config
) : PermissionService, CompactedProcessor<String, User> {

    companion object {
        private const val USER_PERMISSION_TOPIC = "user-permissions-topic"
        internal const val CONSUMER_GROUP = "PERMISSION_SERVICE"
    }

    @Volatile
    private var stopped = false

    @Volatile
    private var snapshotReceived = false

    private val lock = ReentrantLock()
    private var subscription: CompactedSubscription<String, User>? = null

    private var userData: Map<String, User> = mutableMapOf()
    override fun authorizeUser(requestId: String, requestUrl: String, loginName: String, timeoutTimestamp: Instant): Boolean {

        val user = userData[loginName]?:return false

        //Return true for now if the user exist in the local Map
        return true
    }

    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<User>
        get() = User::class.java

    override fun start() {
        lock.withLock {
            if (subscription == null) {
                subscription =
                        subscriptionFactory.createCompactedSubscription(
                                SubscriptionConfig(
                                        CONSUMER_GROUP,
                                        USER_PERMISSION_TOPIC
                                ),
                                this,
                                boostrapConfig
                        )
                subscription!!.start()
                stopped = false
            }
        }
    }

    override fun stop() {
        lock.withLock {
            if (!stopped) {
                subscription?.stop()
                subscription = null

                stopped = true
                snapshotReceived = false
            }
        }
    }

    override fun onSnapshot(currentData: Map<String, User>) {


        snapshotReceived = true
       userData = userData.plus(currentData)
    }

    override fun onNext(
            newRecord: Record<String, User>,
            oldValue: User?,
            currentData: Map<String, User>
    ) {

        userData = userData.plus(Pair(newRecord.key, newRecord.value!!))

    }

}