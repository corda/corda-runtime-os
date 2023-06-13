package net.corda.testing.driver;

import java.util.List;
import java.util.Set;

import net.corda.testing.driver.function.ThrowingSupplier;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.virtualnode.VirtualNodeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@DoNotImplement
public interface DriverDSL {
    @NotNull
    List<VirtualNodeInfo> startNode(@NotNull Set<MemberX500Name> memberNames);

    @Nullable
    String runFlow(
        @NotNull VirtualNodeInfo virtualNodeInfo,
        @NotNull Class<?> flowClass,
        @NotNull ThrowingSupplier<String> flowArgMapper
    );
}
