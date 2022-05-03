package net.corda.processors.member

import net.corda.libs.configuration.SmartConfig

interface MemberProcessor {
    val isRunning: Boolean

    fun start(bootConfig: SmartConfig)

    fun stop()
}