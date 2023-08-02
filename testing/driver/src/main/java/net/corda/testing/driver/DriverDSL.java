package net.corda.testing.driver;

import java.util.List;
import java.util.Set;

import net.corda.testing.driver.function.ThrowingConsumer;
import net.corda.testing.driver.function.ThrowingSupplier;
import net.corda.testing.driver.node.Member;
import net.corda.v5.application.flows.ClientStartableFlow;
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
        @NotNull Class<? extends ClientStartableFlow> flowClass,
        @NotNull ThrowingSupplier<String> flowArgMapper
    );

    void node(
        @NotNull VirtualNodeInfo virtualNodeInfo,
        @NotNull ThrowingConsumer<Member> action
    );

    void groupFor(
        @NotNull VirtualNodeInfo virtualNodeInfo,
        @NotNull ThrowingConsumer<MembershipGroupDSL> action
    );

    void group(
        @NotNull String groupName,
        @NotNull ThrowingConsumer<MembershipGroupDSL> action
    );
}
