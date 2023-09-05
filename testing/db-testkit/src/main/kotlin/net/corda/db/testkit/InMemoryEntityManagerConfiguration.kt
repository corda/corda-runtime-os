package net.corda.db.testkit

import java.sql.SQLException
import kotlin.LazyThreadSafetyMode.SYNCHRONIZED
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.InMemoryDataSourceFactory
import net.corda.db.hsqldb.json.HsqldbJsonExtension
import net.corda.orm.DdlManage
import net.corda.orm.EntityManagerConfiguration
import org.hibernate.boot.MetadataBuilder
import org.hibernate.boot.spi.MetadataBuilderContributor
import org.hibernate.dialect.function.StandardSQLFunction
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl.METADATA_BUILDER_CONTRIBUTOR
import org.hibernate.type.StandardBasicTypes.BOOLEAN
import org.hibernate.type.StandardBasicTypes.STRING
import org.slf4j.LoggerFactory

open class InMemoryEntityManagerConfiguration(dbName: String) : EntityManagerConfiguration {
    init {
        // HSQLDB refuses to use Java functions unless this system property is set correctly.
        System.setProperty("hsqldb.method_class_names", HsqldbJsonExtension::class.java.name + ".*")
    }

    override val dataSource: CloseableDataSource by lazy(SYNCHRONIZED) {
        // SYNCHRONIZED because we have no opportunity to close any extra
        // candidates that could be created via PUBLICATION mode.
        InMemoryDataSourceFactory().create(dbName).also { db ->
            val logger = LoggerFactory.getLogger(this::class.java)
            try {
                if (db.connection.use(HsqldbJsonExtension::setup)) {
                    logger.info("HSQLDB JSON extensions installed.")
                }
            } catch (e: SQLException) {
                logger.warn("Failed to register JSON extensions", e)
            }
        }
    }

    override val ddlManage: DdlManage
        get() = DdlManage.UPDATE

    override val extraProperties: Map<String, Any>
        = mapOf(METADATA_BUILDER_CONTRIBUTOR to HsqldbJsonMetadataContributor())

    private class HsqldbJsonMetadataContributor : MetadataBuilderContributor {
        override fun contribute(metadataBuilder: MetadataBuilder) {
            metadataBuilder
                .applySqlFunction("JsonFieldAsObject", StandardSQLFunction("JsonFieldAsObject", STRING))
                .applySqlFunction("JsonFieldAsText", StandardSQLFunction("JsonFieldAsText", STRING))
                .applySqlFunction("HasJsonKey", StandardSQLFunction("HasJsonKey", BOOLEAN))
        }
    }
}
