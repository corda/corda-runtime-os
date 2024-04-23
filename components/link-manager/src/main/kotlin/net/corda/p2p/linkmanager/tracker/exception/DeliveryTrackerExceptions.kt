package net.corda.p2p.linkmanager.tracker.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

class DataMessageStoreException(msg: String) : CordaRuntimeException(msg)

class DataMessageCacheException(msg: String, cause: Throwable) : CordaRuntimeException(msg, cause)
