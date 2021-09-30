package net.corda.components.examples.persistence.cluster.admin.processor

import net.corda.data.poc.persistence.ClusterAdminEvent
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
import java.sql.Connection

class ClusterAdminEventProcessor(
    private val dbConnection: Connection,
    private val schemaMigrator: LiquibaseSchemaMigrator,
    private val logger: Logger,
    ) : DurableProcessor<String, ClusterAdminEvent>{

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<ClusterAdminEvent>
        get() = ClusterAdminEvent::class.java

    override fun onNext(events: List<Record<String, ClusterAdminEvent>>): List<Record<*, *>> {
        logger.info("Received ${events.map { it.key + "/" + it.value!!.type }}")
        // TODO: I don't think this should be taken from this package
        val dbChange = ClassloaderChangeLog(linkedSetOf(
            ClassloaderChangeLog.ChangeLogResourceFiles(
                ClusterAdminEventProcessor::class.java.packageName,
                listOf("migration/db.changelog-master.xml"),
                classLoader = ClusterAdminEventProcessor::class.java.classLoader)
        ))
        schemaMigrator.updateDb(dbConnection, dbChange)
        return emptyList()
    }
}