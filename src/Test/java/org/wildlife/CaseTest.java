package org.wildlife;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import org.wildlife.Model.*;
import org.wildlife.Service.*;
import org.wildlife.Storage.*;
import static org.wildlife.Model.Types.*;

/** Dependency-free behavioural tests. Failures produce a nonzero process exit code. */
public final class CaseTest {
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static int passed;
    private static final List<Path> TEMPORARIES = new ArrayList<>();
    private CaseTest() { }
    private static final class MemoryRepository implements StateStorage {
        private State state = State.empty(); private boolean fail;
        @Override public State load() { return state; }
        @Override public void save(State next) throws IOException {
            if (fail) throw new IOException("Injected disk failure"); state = next;
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void test(String label, Checked action) throws Exception {
        action.run(); passed++; System.out.printf("PASS T%02d %s%n", passed, label);
    }
    private static void expect(Class<? extends Throwable> type, Checked action) throws Exception {
        try { action.run(); } catch (Exception e) { if (type.isInstance(e)) return; throw e; }
        throw new AssertionError("Expected " + type.getSimpleName());
    }

}

