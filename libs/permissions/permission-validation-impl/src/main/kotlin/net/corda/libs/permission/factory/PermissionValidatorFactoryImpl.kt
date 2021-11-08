package net.corda.libs.permission.factory
import net.corda.libs.permission.GroupTopicProcessor
import net.corda.libs.permission.PermissionValidatorImpl
import net.corda.libs.permission.RoleTopicProcessor
import net.corda.libs.permission.UserTopicProcessor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import net.corda.messaging.api.subscription.factory.SubscriptionFactory

@Component(immediate = true, service = [PermissionValidatorFactory::class])
class PermissionValidatorFactoryImpl @Activate constructor(
        @Reference(service = SubscriptionFactory::class)
        private val subscriptionFactory: SubscriptionFactory
): PermissionValidatorFactory {
    override fun createPermissionValidator(): PermissionValidatorImpl {
        return PermissionValidatorImpl(subscriptionFactory,
            UserTopicProcessor(), GroupTopicProcessor(), RoleTopicProcessor())
    }
}