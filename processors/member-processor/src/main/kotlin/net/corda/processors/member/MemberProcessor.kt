package net.corda.processors.member

import net.corda.libs.configuration.SmartConfig

interface MemberProcessor {
    fun start(bootConfig: SmartConfig)

    fun stop()
}