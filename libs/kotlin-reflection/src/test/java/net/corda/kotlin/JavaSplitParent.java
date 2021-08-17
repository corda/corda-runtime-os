package net.corda.kotlin;

import org.jetbrains.annotations.Nullable;

public abstract class JavaSplitParent implements SplitApi {
    @Override
    @Nullable
    public Object getFirstApi() {
        return "Get-First";
    }

    @Override
    public void setSecondApi(@Nullable Object secondApi) {
    }

    @Override
    @Nullable
    public abstract Object getThirdApi();

    @Override
    public final void setThirdApi(@Nullable Object thirdApi) {
    }

    @Override
    @Nullable
    public final Object getReference() {
        return null;
    }

    @Override
    public final void setReference(@Nullable Object reference) {
    }
}
