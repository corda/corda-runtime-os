package net.corda.crypto.impl

import org.osgi.service.component.annotations.Component
import java.util.UUID

@Component(service = [MemberIdProvider::class])
class MemberIdProviderImpl : MemberIdProvider {
    override val memberId: String
        get() = UUID.randomUUID().toString() // TODO2: implement properly
}