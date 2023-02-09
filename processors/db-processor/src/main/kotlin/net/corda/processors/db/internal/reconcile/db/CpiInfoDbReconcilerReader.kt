package net.corda.processors.db.internal.reconcile.db

import net.corda.libs.cpi.datamodel.repository.CpiMetadataRepositoryImpl
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.reconciliation.VersionedRecord
import java.util.stream.Stream

/**
 * Converts the database entity classes [CpiMetadataEntity] to [CpiMetadata], and also the identifier
 */
val getAllCpiInfoDBVersionedRecords
        : (ReconciliationContext) -> Stream<VersionedRecord<CpiIdentifier, CpiMetadata>> =
    { context ->
        cpiMetadataToVersionedRecords(CpiMetadataRepositoryImpl().findAll(context.getOrCreateEntityManager()))
    }

internal fun cpiMetadataToVersionedRecords(cpiMetadataStream: Stream<CpiMetadata>)
        : Stream<VersionedRecord<CpiIdentifier, CpiMetadata>> =
    cpiMetadataStream.map { cpiMetadata ->
        object : VersionedRecord<CpiIdentifier, CpiMetadata> {
            override val version = cpiMetadata.version
            override val isDeleted = cpiMetadata.isDeleted
            override val key = cpiMetadata.cpiId
            override val value = cpiMetadata // Todo: Before the cpiMetadata was being instantiated in a lazy manner. Now it is created immediately. I am assuming this won't be an issue otherwise the query should be filtered to return a smaller amount of records. This comment should be revised before merging the PR
        }
    }
