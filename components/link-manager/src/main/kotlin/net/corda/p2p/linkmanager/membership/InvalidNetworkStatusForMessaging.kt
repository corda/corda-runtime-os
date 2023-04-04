package net.corda.p2p.linkmanager.membership

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Indicates that two members are not able to message one another and messages should be dropped.
 */
class InvalidNetworkStatusForMessaging(val reason: String) : CordaRuntimeException(reason)