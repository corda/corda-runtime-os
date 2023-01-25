package net.corda.httprpc.server.apigen.test;

import net.corda.httprpc.PluggableRestResource;
import org.jetbrains.annotations.NotNull;

public class TestJavaPrimitivesRestResourceImpl implements TestJavaPrimitivesRestResource, PluggableRestResource<TestJavaPrimitivesRestResource> {
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
  public Class<TestJavaPrimitivesRestResource> getTargetInterface() {
    return TestJavaPrimitivesRestResource.class;
  }

  @Override
  public int getProtocolVersion() {
    return 2;
  }
}
