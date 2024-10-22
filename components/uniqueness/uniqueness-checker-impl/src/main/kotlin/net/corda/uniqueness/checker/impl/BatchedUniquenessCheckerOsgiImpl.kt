package net.corda.uniqueness.checker.impl

import net.corda.ledger.libs.uniqueness.UniquenessChecker
import net.corda.ledger.libs.uniqueness.UniquenessCheckerMetricsFactory
import net.corda.ledger.libs.uniqueness.UniquenessSecureHashFactory
import net.corda.ledger.libs.uniqueness.backingstore.BackingStore
import net.corda.ledger.libs.uniqueness.impl.BatchedUniquenessCheckerImpl
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ UniquenessChecker::class ])
class BatchedUniquenessCheckerOsgiImpl(
    backingStore: BackingStore,
    uniquenessCheckerMetricsFactory: UniquenessCheckerMetricsFactory,
    uniquenessSecureHashFactory: UniquenessSecureHashFactory,
    clock: Clock
) : BatchedUniquenessCheckerImpl(backingStore, uniquenessCheckerMetricsFactory, uniquenessSecureHashFactory, clock) {

    @Activate
    constructor(
        @Reference(service = BackingStore::class)
        backingStore: BackingStore,
        @Reference(service = UniquenessCheckerMetricsFactory::class)
        uniquenessCheckerMetricsFactory: UniquenessCheckerMetricsFactory,
        @Reference(service = UniquenessSecureHashFactory::class)
        uniquenessSecureHashFactory: UniquenessSecureHashFactory
    ) : this(backingStore, uniquenessCheckerMetricsFactory, uniquenessSecureHashFactory, UTCClock())
}
