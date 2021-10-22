package net.corda.libs.permission.factory
import com.typesafe.config.Config
import net.corda.libs.permission.PermissionServiceImpl
import net.corda.libs.permission.PermissionsTopicProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import net.corda.messaging.api.subscription.factory.SubscriptionFactory

@Component(immediate = true, service = [PermissionServiceFactory::class])
class PermissionServiceFactoryImpl @Activate constructor(
        @Reference(service = SubscriptionFactory::class)
        private val subscriptionFactory: SubscriptionFactory
): PermissionServiceFactory {
    override fun createPermissionService(bootstrapConfig: Config): PermissionServiceImpl {
        return PermissionServiceImpl(subscriptionFactory, PermissionsTopicProcessor())
    }
}