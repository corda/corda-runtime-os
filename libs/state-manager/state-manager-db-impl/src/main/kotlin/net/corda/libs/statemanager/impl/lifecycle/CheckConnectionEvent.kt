package net.corda.libs.statemanager.impl.lifecycle

import net.corda.lifecycle.TimerEvent

data class CheckConnectionEvent(override val key: String) : TimerEvent
