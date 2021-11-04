package net.corda.libs.permission.factory
import net.corda.libs.permission.PermissionServiceImpl
import net.corda.libs.permission.UserTopicProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import net.corda.messaging.api.subscription.factory.SubscriptionFactory

@Component(immediate = true, service = [PermissionServiceFactory::class])
class PermissionServiceFactoryImpl @Activate constructor(
        @Reference(service = SubscriptionFactory::class)
        private val subscriptionFactory: SubscriptionFactory
): PermissionServiceFactory {
    override fun createPermissionService(): PermissionServiceImpl {
        return PermissionServiceImpl(subscriptionFactory, UserTopicProcessor())
    }
}