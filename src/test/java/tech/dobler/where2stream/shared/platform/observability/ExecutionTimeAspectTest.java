package tech.dobler.where2stream.shared.platform.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionTimeAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private Signature signature;

    private final ExecutionTimeAspect aspect = new ExecutionTimeAspect();

    @Test
    void returnsTheWrappedMethodsResultUnchanged() throws Throwable {
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn("result");

        final var result = aspect.logExecutionTime(joinPoint);

        assertThat(result).isEqualTo("result");
    }

    @Test
    void propagatesAnExceptionFromTheWrappedMethod() throws Throwable {
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        final var failure = new IllegalStateException("boom");
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.logExecutionTime(joinPoint)).isSameAs(failure);
    }
}
