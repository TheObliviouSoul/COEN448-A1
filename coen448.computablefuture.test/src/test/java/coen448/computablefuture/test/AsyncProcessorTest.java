package coen448.computablefuture.test;

// Tests verify fail-fast, fail-partial, and fail-soft semantics (no Mockito)
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncProcessorTest {

    // ---- Test helpers (NO Mockito) ----

    /** Always succeeds immediately with a deterministic value. */
    static class AlwaysOkMicroservice extends Microservice {
        private final String fixedResult;

        AlwaysOkMicroservice(String serviceId, String fixedResult) {
            super(serviceId);
            this.fixedResult = fixedResult;
        }

        @Override
        public CompletableFuture<String> retrieveAsync(String input) {
            return CompletableFuture.completedFuture(fixedResult);
        }
    }

    /** Always fails immediately. */
    static class AlwaysFailMicroservice extends Microservice {
        private final RuntimeException ex;

        AlwaysFailMicroservice(String serviceId, RuntimeException ex) {
            super(serviceId);
            this.ex = ex;
        }

        @Override
        public CompletableFuture<String> retrieveAsync(String input) {
            CompletableFuture<String> cf = new CompletableFuture<>();
            cf.completeExceptionally(ex);
            return cf;
        }
    }

    // ---- Existing baseline method tests (processAsync) ----

    @RepeatedTest(5)
    public void testProcessAsyncSuccess_noMockito() throws Exception {
        Microservice s1 = new AlwaysOkMicroservice("S1", "Hello");
        Microservice s2 = new AlwaysOkMicroservice("S2", "World");

        AsyncProcessor processor = new AsyncProcessor();
        CompletableFuture<String> resultFuture = processor.processAsync(List.of(s1, s2), "ignored");

        String result = resultFuture.get(1, TimeUnit.SECONDS);
        assertEquals("Hello World", result);
    }

    @ParameterizedTest
    @CsvSource({
            "hi, Hello:HI World:HI",
            "cloud, Hello:CLOUD World:CLOUD",
            "async, Hello:ASYNC World:ASYNC"
    })
    public void testProcessAsync_withDifferentMessages(String message, String expectedResult)
            throws Exception {

        Microservice service1 = new Microservice("Hello");
        Microservice service2 = new Microservice("World");

        AsyncProcessor processor = new AsyncProcessor();

        CompletableFuture<String> resultFuture = processor.processAsync(List.of(service1, service2), message);

        String result = resultFuture.get(1, TimeUnit.SECONDS);
        assertEquals(expectedResult, result);
    }

    // ---- Required category: Nondeterminism observed (not asserted) ----

    @RepeatedTest(20)
    void showNondeterminism_completionOrderVaries() throws Exception {
        Microservice s1 = new Microservice("A");
        Microservice s2 = new Microservice("B");
        Microservice s3 = new Microservice("C");

        AsyncProcessor processor = new AsyncProcessor();

        List<String> order = processor
                .processAsyncCompletionOrder(List.of(s1, s2, s3), "msg")
                .get(1, TimeUnit.SECONDS);

        // Not asserting a fixed order (intentionally nondeterministic)
        System.out.println(order);

        // Minimal sanity: all three must be present
        assertEquals(3, order.size());
        assertTrue(order.stream().anyMatch(x -> x.startsWith("A:")));
        assertTrue(order.stream().anyMatch(x -> x.startsWith("B:")));
        assertTrue(order.stream().anyMatch(x -> x.startsWith("C:")));
    }

    // ---- NEW POLICY TESTS (these require you to implement methods in
    // AsyncProcessor) ----

    @Test
    void failFast_failurePropagates() {
        AsyncProcessor processor = new AsyncProcessor();

        List<Microservice> services = List.of(
                new AlwaysOkMicroservice("OK1", "R1"),
                new AlwaysFailMicroservice("BAD", new RuntimeException("boom")),
                new AlwaysOkMicroservice("OK2", "R3"));

        List<String> messages = List.of("m1", "m2", "m3");

        CompletableFuture<String> f = processor.processAsyncFailFast(services, messages);

        // get(...) wraps exceptions in ExecutionException
        assertThrows(ExecutionException.class, () -> f.get(1, TimeUnit.SECONDS));
    }

    @Test
    void failPartial_returnsOnlySuccessfulResults_noExceptionEscapes() throws Exception {
        AsyncProcessor processor = new AsyncProcessor();

        List<Microservice> services = List.of(
                new AlwaysOkMicroservice("OK1", "R1"),
                new AlwaysFailMicroservice("BAD", new RuntimeException("boom")),
                new AlwaysOkMicroservice("OK2", "R3"));

        List<String> messages = List.of("m1", "m2", "m3");

        CompletableFuture<List<String>> f = processor.processAsyncFailPartial(services, messages);

        List<String> results = f.get(1, TimeUnit.SECONDS);

        // Best-effort: should contain the successful outputs; failed one is omitted (or
        // marker if you choose that design)
        assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.contains("R1"));
        assertTrue(results.contains("R3"));
    }

    @Test
    void failSoft_usesFallbackValues_neverFails() throws Exception {
        AsyncProcessor processor = new AsyncProcessor();

        List<Microservice> services = List.of(
                new AlwaysOkMicroservice("OK1", "R1"),
                new AlwaysFailMicroservice("BAD", new RuntimeException("boom")),
                new AlwaysOkMicroservice("OK2", "R3"));

        List<String> messages = List.of("m1", "m2", "m3");
        String fallback = "FALLBACK";

        CompletableFuture<String> f = processor.processAsyncFailSoft(services, messages, fallback);

        String out = f.get(1, TimeUnit.SECONDS);

        assertNotNull(out);
        // Your fail-soft output should include fallback in the correct "slot" for the
        // failed service
        assertTrue(out.contains("R1"));
        assertTrue(out.contains("R3"));
        assertTrue(out.contains(fallback));
    }

    @Test
    void liveness_allPoliciesCompleteWithinTimeout() {
        AsyncProcessor processor = new AsyncProcessor();

        List<Microservice> services = List.of(
                new AlwaysOkMicroservice("OK1", "R1"),
                new AlwaysFailMicroservice("BAD", new RuntimeException("boom")),
                new AlwaysOkMicroservice("OK2", "R3"));
        List<String> messages = List.of("m1", "m2", "m3");

        assertDoesNotThrow(() -> processor.processAsyncFailPartial(services, messages).get(1, TimeUnit.SECONDS));
        assertDoesNotThrow(
                () -> processor.processAsyncFailSoft(services, messages, "FALLBACK").get(1, TimeUnit.SECONDS));

        // Fail-fast completes too (exceptionally). Still must not hang.
        assertThrows(ExecutionException.class,
                () -> processor.processAsyncFailFast(services, messages).get(1, TimeUnit.SECONDS));
    }

    @Test
    void failFast_noPartialResultReturned() {
        AsyncProcessor processor = new AsyncProcessor();

        // One failure should make the whole thing exceptional; there is no "partial
        // string".
        List<Microservice> services = List.of(
                new AlwaysOkMicroservice("OK1", "R1"),
                new AlwaysFailMicroservice("BAD", new RuntimeException("boom")),
                new AlwaysOkMicroservice("OK2", "R3"));
        List<String> messages = List.of("m1", "m2", "m3");

        CompletableFuture<String> f = processor.processAsyncFailFast(services, messages);

        assertTrue(f.isDone() || !f.isDone()); // (just to avoid timing assumptions)
        assertThrows(ExecutionException.class, () -> f.get(1, TimeUnit.SECONDS));
    }
}
