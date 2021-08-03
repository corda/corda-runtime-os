package net.corda.v5.application.node

import net.corda.v5.application.injection.CordaFlowInjectable
import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.serialization.SerializeAsToken
import net.corda.v5.serialization.SerializeAsTokenContext
import net.corda.v5.serialization.SingletonSerializationToken
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** A [Clock] that tokenizes itself when serialized, and delegates to an underlying [Clock] implementation. */
@DoNotImplement
abstract class CordaClock : Clock(), SerializeAsToken, CordaServiceInjectable, CordaFlowInjectable {
    protected abstract val delegateClock: Clock
    private val token = SingletonSerializationToken.singletonSerializationToken(javaClass)
    override fun toToken(context: SerializeAsTokenContext) = token.registerWithContext(context, this)
    override fun instant(): Instant = delegateClock.instant()
    override fun getZone(): ZoneId = delegateClock.zone
    @Deprecated("Do not use this. Instead seek to use ZonedDateTime methods.", level = DeprecationLevel.ERROR)
    override fun withZone(zone: ZoneId) = throw UnsupportedOperationException("Tokenized clock does not support withZone()")
}