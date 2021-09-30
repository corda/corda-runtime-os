package net.corda.httprpc.server.apigen.test;

import net.corda.httprpc.PluggableRPCOps;
import org.jetbrains.annotations.NotNull;

public class TestJavaPrimitivesRPCopsImpl implements TestJavaPrimitivesRpcOps, PluggableRPCOps<TestJavaPrimitivesRpcOps> {
  @Override
  public Integer negateInt(Integer number) {
    return -number;
  }

  @Override
  public int negatePrimitiveInt(int number) {
    return -number;
  }

  @Override
  public Long negateLong(Long number) {
    return -number;
  }

  @Override
  public Boolean negateBoolean(Boolean bool) {
    return !bool;
  }

  @Override
  public String reverse(String text) {
    return new StringBuilder(text).reverse().toString();
  }

  @NotNull
  @Override
  public Class<TestJavaPrimitivesRpcOps> getTargetInterface() {
    return TestJavaPrimitivesRpcOps.class;
  }

  @Override
  public int getProtocolVersion() {
    return 2;
  }
}
