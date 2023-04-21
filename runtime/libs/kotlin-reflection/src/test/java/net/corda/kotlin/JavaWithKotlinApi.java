package net.corda.kotlin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@SuppressWarnings("unused")
public class JavaWithKotlinApi implements Api {
    @Override
    @NotNull
    public String getFirstVal() {
        return "GetFirstVal";
    }

    @Override
    @NotNull
    public String getFirstVar() {
        return "GetFirstVar";
    }

    @Override
    public void setFirstVar(@NotNull String firstVar) {
    }

    @Override
    public void greet(@NotNull String name) {
    }

    @Override
    public void shareApi(@NotNull Object item) {
    }

    @Override
    @NotNull
    public String getShareApiProp() {
        return "GetShareApiProp";
    }

    @Override
    public void setShareApiProp(@NotNull String shareApiProp) {
    }

    @NotNull
    @Override
    public String getApiShow(@NotNull Collection<?> $this$getApiShow) {
        return "GetApiShow";
    }

    @Override
    public void setApiShow(@NotNull Collection<?> $this$setApiShow, @NotNull String apiShow) {
    }

    @Override
    public boolean testFunc(@NotNull byte[] $this$test, @NotNull Object obj) {
        return false;
    }

    @Override
    @Nullable
    public Object anything() {
        return null;
    }

    @Override
    public void anything(int index) {
    }

    @Nullable
    @Override
    public Integer getPrimitiveIntVal() {
        return null;
    }
}
