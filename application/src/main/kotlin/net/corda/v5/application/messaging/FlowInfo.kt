package net.corda.v5.application.messaging

import net.corda.v5.base.annotations.CordaSerializable

/**
 * Version and name of the CorDapp hosting the other side of the flow.
 */
@CordaSerializable
data class FlowInfo(
        /**
         * The integer flow version the other side is using.
         * @see InitiatingFlow
         */
        val flowVersion: Int,
        /**
         * Name of the CorDapp jar hosting the flow, without the .jar extension. It will include a unique identifier
         * to deduplicate it from other releases of the same CorDapp, typically a version string. See the
         * [CorDapp JAR format](https://docs.corda.net/cordapp-build-systems.html#cordapp-jar-format) for more details.
         */
        val appName: String)
