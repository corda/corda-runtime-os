package net.corda.libs.permission

import net.corda.data.permissions.User
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import java.util.*

class UserTopicProcessor : CompactedProcessor<String, User> {

    private val userData: MutableMap<String, User> = Collections.synchronizedMap(mutableMapOf())

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