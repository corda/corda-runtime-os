package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable
import java.time.Duration

@CordaSerializable
class CryptoServiceTimeoutException(timeout: Duration) :
    CryptoServiceException("Timed-out while waiting for ${timeout.toMillis()} milliseconds")
