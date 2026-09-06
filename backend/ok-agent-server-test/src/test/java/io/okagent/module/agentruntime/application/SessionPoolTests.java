package io.okagent.module.agentruntime.application;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
class SessionPoolTests {
    @Test void activeSessionsCannotBeReplacedOrEvicted() throws Exception {
        var pool = new SessionPool<AutoCloseable>(1);
        var first = mock(AutoCloseable.class);
        var lease = pool.acquire("a", "v1", () -> first);
        assertThatThrownBy(() -> pool.acquire("a", "v2", () -> first)).hasMessageContaining("already processing");
        assertThatThrownBy(() -> pool.acquire("b", "v1", () -> first)).hasMessageContaining("busy");
        verify(first, never()).close();
        lease.close();
        var second = mock(AutoCloseable.class);
        var next = pool.acquire("b", "v1", () -> second);
        verify(first).close();
        pool.close();
        verify(second, never()).close();
        next.close();
        verify(second).close();
    }
    @Test void failedConstructionDoesNotConsumeCapacity() {
        var pool = new SessionPool<AutoCloseable>(1);
        assertThatThrownBy(() -> pool.acquire("a", "v", () -> { throw new IllegalStateException("failed"); })).hasMessage("failed");
        try (var lease = pool.acquire("b", "v", () -> () -> {})) { assertThat(lease.value()).isNotNull(); }
    }
}
