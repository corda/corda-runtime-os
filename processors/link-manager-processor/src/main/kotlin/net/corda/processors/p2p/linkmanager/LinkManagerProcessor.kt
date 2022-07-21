package net.corda.processors.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.p2p.linkmanager.LinkManager
import net.corda.p2p.linkmanager.ThirdPartyComponentsMode

/**
 * The processor for a [LinkManager].
 * */
interface LinkManagerProcessor {

    fun start(bootConfig: SmartConfig, useStubComponents: Boolean)

    fun stop()
}