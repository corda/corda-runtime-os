package net.corda.entityprocessor.impl.tests.fake

import net.corda.cpiinfo.read.CpiInfoListener
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.VersionedRecord
import java.util.stream.Stream

/**
 * Intentional - we're going to override a few specific methods elsewhere.
 */
open class FakeCpiInfoReadService : CpiInfoReadService {
    override fun getAll(): List<CpiMetadata> {
        TODO("Not yet implemented")
    }

    override fun get(identifier: CpiIdentifier): CpiMetadata? {
        TODO("Not yet implemented")
    }

    override fun registerCallback(listener: CpiInfoListener): AutoCloseable {
        TODO("Not yet implemented")
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<CpiIdentifier, CpiMetadata>>? {
        TODO("Not yet implemented")
    }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = TODO("Not yet implemented")

    override val isRunning: Boolean
        get() = TODO("Not yet implemented")

    override fun start() {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }
}