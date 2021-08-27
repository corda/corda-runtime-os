package net.corda.kotlin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaSplitChild extends JavaSplitParent {
    @Override
    public void setFirstApi(@Nullable Object firstApi) {
    }

    @Override
    @Nullable
    public Object getSecondApi() {
        return "Get-Second";
    }

    @Override
    @NotNull
    public final Object getThirdApi() {
        return "Get-Third";
    }
}
