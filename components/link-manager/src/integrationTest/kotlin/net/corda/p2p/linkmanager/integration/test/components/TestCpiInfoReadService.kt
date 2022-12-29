package net.corda.p2p.linkmanager.integration.test.components

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.VersionedRecord
import java.util.stream.Stream

internal class TestCpiInfoReadService(
    coordinatorFactory: LifecycleCoordinatorFactory,
): CpiInfoReadService, TestLifeCycle(
    coordinatorFactory,
    CpiInfoReadService::class
) {
    override fun getAll(): Collection<CpiMetadata> {
        throw UnsupportedOperationException()
    }

    override fun get(identifier: CpiIdentifier): CpiMetadata? {
        throw UnsupportedOperationException()
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<CpiIdentifier, CpiMetadata>>? {
        throw UnsupportedOperationException()
    }

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<CpiInfoReadService>()
}