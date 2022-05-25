package com.example.securitymanager.two.flows;

import net.corda.v5.application.flows.Flow;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.lang.reflect.Field;

@Component
public class ReflectionJavaFlow implements Flow<String> {
    @Activate
    public ReflectionJavaFlow() {
        super();
    }

    private class Test {
        private String value;

        public Test(String value) {
            this.value = value;
        }
    }

    @Override
    public String call() {
        Test test = new Test("test");
        try {
            Field field = Test.class.getDeclaredField("value");
            field.setAccessible(true);
            return (String) field.get(test);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
