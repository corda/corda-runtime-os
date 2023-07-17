package net.corda.testing.driver.node;

import net.corda.data.virtualnode.VirtualNodeInfo;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@DoNotImplement
@FunctionalInterface
public interface RunFlow {
    @Nullable
    String runFlow(
        @NotNull VirtualNodeInfo virtualNodeInfo,
        @NotNull String flowClassName,
        @NotNull String flowStartArgs
    );
}
