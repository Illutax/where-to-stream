package tech.dobler.where2stream.shared.platform.observability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose execution time is logged by {@link ExecutionTimeAspect}.
 * Like any Spring AOP advice, this only takes effect on calls that go through the bean's proxy —
 * a self-invocation (a method calling another on {@code this}) bypasses it, the same caveat that
 * applies to {@code @Transactional} (see the NOTE in {@code StreamInfoService}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogExecutionTime {
}
