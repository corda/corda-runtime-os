package net.corda.testing.driver.node;

import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

@DoNotImplement
public interface Member {
    @NotNull
    MemberStatus getStatus();

    void setStatus(@NotNull MemberStatus status);
}
