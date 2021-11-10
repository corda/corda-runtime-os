package net.corda.libs.permission.impl

import net.corda.data.permissions.User
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

class UserTopicProcessor : CompactedProcessor<String, User> {

    @VisibleForTesting
    internal val userData: MutableMap<String, User> = ConcurrentHashMap()

    fun getUser(userLogin: String) = userData[userLogin]

    override val keyClass = String::class.java
    override val valueClass = User::class.java

    override fun onSnapshot(currentData: Map<String, User>) {
        userData.putAll(currentData)
    }

    override fun onNext(
        newRecord: Record<String, User>,
        oldValue: User?,
        currentData: Map<String, User>
    ) {
        val user = newRecord.value
        val userLogin = newRecord.key

        if (user == null) {
            userData.remove(userLogin)
        } else {
            userData[userLogin] = user
        }
    }
}