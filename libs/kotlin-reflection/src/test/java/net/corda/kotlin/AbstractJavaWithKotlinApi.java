package net.corda.kotlin;

import org.jetbrains.annotations.NotNull;

public abstract class AbstractJavaWithKotlinApi implements Api {
    @Override
    @NotNull
    public String getFirstVal() {
        return "GetFirstVal";
    }

    @NotNull
    @Override
    public String getFirstVar() {
        return "GetFirstVar";
    }

    @Override
    public void setFirstVar(@NotNull String firstVar) {
    }

    @Override
    public Integer getPrimitiveIntVal() {
        return -1;
    }
}
