package net.corda.membership.lib.impl

import net.corda.data.membership.PersistentMemberInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.layeredpropertymap.create
import net.corda.membership.lib.MemberInfoFactory
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.*

@Component(service = [MemberInfoFactory::class])
class MemberInfoFactoryImpl @Activate constructor(
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory
) : MemberInfoFactory {

    override fun create(
        memberContext: MemberContext,
        mgmContext: MGMContext
    ) = MemberInfoImpl(memberContext, mgmContext)

    override fun create(
        memberContext: SortedMap<String, String?>,
        mgmContext: SortedMap<String, String?>
    ) = with(layeredPropertyMapFactory) {
        create(
            create<MemberContextImpl>(memberContext),
            create<MGMContextImpl>(mgmContext)
        )
    }

    override fun create(
        memberInfo: PersistentMemberInfo
    ) = with(memberInfo) {
        create(
            memberContext.toSortedMap(),
            mgmContext.toSortedMap()
        )
    }
}