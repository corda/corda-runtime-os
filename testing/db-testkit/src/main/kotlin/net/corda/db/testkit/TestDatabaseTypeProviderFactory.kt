package net.corda.db.testkit

import java.util.Hashtable
import net.corda.orm.DatabaseType
import net.corda.orm.DatabaseType.HSQLDB
import net.corda.orm.DatabaseType.POSTGRES
import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.DATABASE_TYPE
import org.osgi.annotation.bundle.Capability
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants.EFFECTIVE_ACTIVE
import org.osgi.framework.Constants.OBJECTCLASS
import org.osgi.framework.Constants.SERVICE_RANKING
import org.osgi.framework.ServiceRegistration
import org.osgi.service.component.ComponentConstants.COMPONENT_NAME
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.runtime.ServiceComponentRuntime

private class TestDatabaseTypeProvider(override val databaseType: DatabaseType) : DatabaseTypeProvider

@Suppress("unused")
@Capability(
    namespace = "osgi.service",
    attribute = [ "$OBJECTCLASS:List<String>='net.corda.orm.DatabaseTypeProvider'" ],
    effective = EFFECTIVE_ACTIVE
)
@Component(service = [], immediate = true)
class TestDatabaseTypeProviderFactory @Activate constructor(
    @Reference
    scr: ServiceComponentRuntime,
    bundleContext: BundleContext
) {
    private companion object {
        private const val COMPONENT_NAME_FILTER = "($COMPONENT_NAME=*)"
        private const val DATABASE_TYPE_SERVICE_RANKING = Int.MAX_VALUE / 2
    }

    private val reference: ServiceRegistration<DatabaseTypeProvider>

    init {
        val databaseType = if (DbUtils.isInMemory) {
            HSQLDB
        } else {
            POSTGRES
        }

        // Create a DatabaseTypeProvider for our configured DBMS.
        // This service should outrank the default provider.
        reference = bundleContext.registerService(
            DatabaseTypeProvider::class.java,
            TestDatabaseTypeProvider(databaseType),
            Hashtable<String, Any>().also { props ->
                props[DATABASE_TYPE] = databaseType.value
                props[SERVICE_RANKING] = DATABASE_TYPE_SERVICE_RANKING
            }
        )

        // Deactivate any existing DatabaseTypeProvider components, including the default one.
        bundleContext.getServiceReferences(DatabaseTypeProvider::class.java, COMPONENT_NAME_FILTER).forEach { svcRef ->
            val componentName = svcRef.properties[COMPONENT_NAME] ?: return@forEach
            scr.getComponentDescriptionDTO(svcRef.bundle, componentName.toString())?.also(scr::disableComponent)
        }
    }

    @Deactivate
    fun done() {
        reference.unregister()
    }
}
