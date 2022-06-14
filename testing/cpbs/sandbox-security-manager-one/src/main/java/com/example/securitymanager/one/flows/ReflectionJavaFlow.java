package com.example.securitymanager.one.flows;

import net.corda.v5.application.flows.Subflow;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import java.lang.reflect.Field;

@Component
public class ReflectionJavaFlow implements Subflow<String> {
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
