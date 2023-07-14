package net.corda.testing.driver;

import java.util.Set;
import net.corda.testing.driver.function.ThrowingConsumer;
import net.corda.testing.driver.node.Member;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

@DoNotImplement
public interface MembershipGroupDSL {
    @NotNull
    @Unmodifiable
    Set<MemberX500Name> members();

    void member(@NotNull MemberX500Name name, @NotNull ThrowingConsumer<Member> action);
}
