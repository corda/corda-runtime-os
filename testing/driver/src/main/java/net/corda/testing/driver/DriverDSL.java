package net.corda.testing.driver;

import java.util.List;
import java.util.Map;
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
    List<@NotNull VirtualNodeInfo> startNodes(@NotNull @Unmodifiable Set<@NotNull MemberX500Name> memberNames);

    @NotNull
    VirtualNodeInfo nodeFor(@NotNull String groupName, @NotNull MemberX500Name member);

    @NotNull
    @Unmodifiable
    Map<@NotNull MemberX500Name, @NotNull VirtualNodeInfo> nodesFor(@NotNull String groupName);

    @Nullable
    String runFlow(
        @NotNull VirtualNodeInfo virtualNodeInfo,
        @NotNull Class<? extends ClientStartableFlow> flowClass,
        @NotNull ThrowingSupplier<@NotNull String> flowArgMapper
    );

    void node(
        @NotNull VirtualNodeInfo virtualNodeInfo,
        @NotNull ThrowingConsumer<@NotNull Member> action
    );

    void node(
        @NotNull String groupName,
        @NotNull MemberX500Name memberName,
        @NotNull ThrowingConsumer<@NotNull Member> action
    );

    void groupFor(
        @NotNull VirtualNodeInfo virtualNodeInfo,
        @NotNull ThrowingConsumer<@NotNull MembershipGroupDSL> action
    );

    void group(
        @NotNull String groupName,
        @NotNull ThrowingConsumer<@NotNull MembershipGroupDSL> action
    );
}
