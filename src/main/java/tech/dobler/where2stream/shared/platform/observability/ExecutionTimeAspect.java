package tech.dobler.where2stream.shared.platform.observability;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Logs how long each {@link LogExecutionTime}-annotated method took, on both the success and the
 * failure path, so a slow request can be traced to the stage that actually took the time
 * (e.g. the batch cache read vs. the per-title upstream fetch in {@code StreamInfoService}).
 */
@Slf4j
@Aspect
@Component
public class ExecutionTimeAspect {

    @Around("@annotation(LogExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        final long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            final long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.info("{} took {}ms", joinPoint.getSignature().toShortString(), elapsedMs);
        }
    }
}
