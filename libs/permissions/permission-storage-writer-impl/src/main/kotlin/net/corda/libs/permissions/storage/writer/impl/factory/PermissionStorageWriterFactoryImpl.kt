package net.corda.libs.permissions.storage.writer.impl.factory

import net.corda.libs.permissions.storage.writer.PermissionStorageWriter
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterFactory
import net.corda.libs.permissions.storage.writer.impl.PermissionStorageWriterImpl
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

@Component(service = [PermissionStorageWriterFactory::class])
class PermissionStorageWriterFactoryImpl @Activate constructor(
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
) : PermissionStorageWriterFactory {

    override fun create(entityManagerFactory: EntityManagerFactory): PermissionStorageWriter {
        return PermissionStorageWriterImpl(subscriptionFactory, entityManagerFactory)
    }
}