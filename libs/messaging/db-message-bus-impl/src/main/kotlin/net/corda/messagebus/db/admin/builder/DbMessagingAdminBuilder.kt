package net.corda.messagebus.db.admin.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.admin.Admin
import net.corda.messagebus.api.admin.builder.AdminBuilder
import net.corda.messagebus.api.configuration.AdminConfig
import net.corda.messagebus.db.admin.DbMessagingAdmin
import net.corda.messagebus.db.configuration.MessageBusConfigResolver
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.EntityManagerFactoryHolder
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [AdminBuilder::class])
class DbMessagingAdminBuilder @Activate constructor(
    @Reference(service = EntityManagerFactoryHolder::class)
    private val entityManagerFactoryHolder: EntityManagerFactoryHolder,
) : AdminBuilder {
    override fun createAdmin(adminConfig: AdminConfig, messageBusConfig: SmartConfig): Admin {
        val resolver = MessageBusConfigResolver(messageBusConfig.factory)
        val resolvedConfig = resolver.resolve(messageBusConfig, adminConfig)

        val emf = entityManagerFactoryHolder.getEmf(
            resolvedConfig.jdbcUrl,
            resolvedConfig.jdbcUser,
            resolvedConfig.jdbcPass
        )

        return DbMessagingAdmin(DBAccess(emf))
    }
}
