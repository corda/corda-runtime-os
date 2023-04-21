package net.corda.messagebus.db.admin

import net.corda.messagebus.api.admin.Admin
import net.corda.messagebus.db.persistence.DBAccess

class DbMessagingAdmin(
    private val dbAccess: DBAccess
) : Admin {

    override fun getTopics(): Set<String> {
        return dbAccess.getAllTopics()
    }

    override fun close() {
        // Does nothing on DB implementation
    }
}
