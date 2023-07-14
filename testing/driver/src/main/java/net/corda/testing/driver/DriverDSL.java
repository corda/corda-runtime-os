package net.corda.testing.driver;

import java.util.List;
import java.util.Set;

import net.corda.testing.driver.function.ThrowingConsumer;
import net.corda.testing.driver.function.ThrowingSupplier;
import net.corda.testing.driver.node.Member;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.virtualnode.VirtualNodeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

@DoNotImplement
public interface DriverDSL {
    @NotNull
    @Unmodifiable
    List<VirtualNodeInfo> startNodes(@NotNull @Unmodifiable Set<MemberX500Name> memberNames);

    @Nullable
    String runFlow(
        @NotNull VirtualNodeInfo virtualNodeInfo,
        @NotNull Class<?> flowClass,
        @NotNull ThrowingSupplier<String> flowArgMapper
    );

    void member(
        @NotNull VirtualNodeInfo vNode,
        @NotNull ThrowingConsumer<Member> action
    );

    void groupOf(
        @NotNull VirtualNodeInfo vNode,
        @NotNull ThrowingConsumer<MembershipGroupDSL> action
    );
}
