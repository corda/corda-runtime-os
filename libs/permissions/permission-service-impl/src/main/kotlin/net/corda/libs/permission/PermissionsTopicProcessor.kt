package net.corda.libs.permission

import net.corda.data.permissions.User
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.annotations.VisibleForTesting

class PermissionsTopicProcessor : CompactedProcessor<String, User> {

    @VisibleForTesting
    var userData: Map<String, User> = mutableMapOf()

    fun getUser(id: String) = userData[id]

    override val keyClass = String::class.java
    override val valueClass = User::class.java

    override fun onSnapshot(currentData: Map<String, User>) {
        userData = userData.plus(currentData)
    }

    override fun onNext(
            newRecord: Record<String, User>,
            oldValue: User?,
            currentData: Map<String, User>
    ) {
        val user = newRecord.value
        val userLogin = newRecord.key

        userData = if (user == null) {
            userData.minus(userLogin)
        } else {
            userData.plus(Pair(userLogin, user))
        }
    }
}