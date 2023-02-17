package net.corda.db.connection.manager.impl

import net.corda.lifecycle.TimerEvent

data class CheckDbEvent(override val key: String) : TimerEvent
