package net.corda.testing.driver.node;

import net.corda.data.virtualnode.VirtualNodeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface RunFlow {
    @Nullable
    String runFlow(
        @NotNull VirtualNodeInfo virtualNodeInfo,
        @NotNull String flowClassName,
        @NotNull String flowStartArgs
    );
}
