package net.corda.testing.driver.node;

import java.util.Set;
import net.corda.data.identity.HoldingIdentity;
import net.corda.testing.driver.function.ThrowingConsumer;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

@DoNotImplement
public interface MembershipGroup {
    @NotNull
    String getName(@NotNull HoldingIdentity holdingIdentity);

    @NotNull
    HoldingIdentity getAnyMemberOf(@NotNull String groupName);

    @NotNull
    @Unmodifiable
    Set<MemberX500Name> getMembers(@NotNull HoldingIdentity holdingIdentity);

    void virtualNode(@NotNull HoldingIdentity holdingIdentity, @NotNull ThrowingConsumer<Member> action);
}
