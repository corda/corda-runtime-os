package net.corda.testing.driver.node;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;

@DoNotImplement
public interface Member {
    @NotNull
    MemberX500Name getName();

    @NotNull
    MemberStatus getStatus();

    void setStatus(@NotNull MemberStatus status);
}
