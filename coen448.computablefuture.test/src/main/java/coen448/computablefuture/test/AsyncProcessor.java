package coen448.computablefuture.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AsyncProcessor {

    public CompletableFuture<String> processAsync(List<Microservice> microservices, String message) {

        List<CompletableFuture<String>> futures = microservices.stream()
                .map(client -> client.retrieveAsync(message))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.joining(" ")));
    }

    public CompletableFuture<List<String>> processAsyncCompletionOrder(
            List<Microservice> microservices, String message) {

        List<String> completionOrder = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = microservices.stream()
                .map(ms -> ms.retrieveAsync(message)
                        .thenAccept(completionOrder::add))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> completionOrder);
    }

    /**
     * Task A — Fail-Fast (Atomic Policy)
     * If any service fails, the returned future completes exceptionally.
     * No partial result is returned.
     */
    public CompletableFuture<String> processAsyncFailFast(
            List<Microservice> services,
            List<String> messages) {
        if (services.size() != messages.size()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("services and messages must have the same size"));
        }

        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < services.size(); i++) {
            futures.add(services.get(i).retrieveAsync(messages.get(i)));
        }

        // allOf completes exceptionally if any future completes exceptionally.
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join) // safe because all completed normally
                        .collect(Collectors.joining(" ")));
    }

    /**
     * Task B — Fail-Partial (Best-Effort Policy)
     * Per-service failures are handled; only successful results are returned.
     * The returned future never completes exceptionally.
     */
    public CompletableFuture<List<String>> processAsyncFailPartial(
            List<Microservice> services,
            List<String> messages) {
        if (services.size() != messages.size()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<CompletableFuture<String>> guarded = new ArrayList<>();
        for (int i = 0; i < services.size(); i++) {
            CompletableFuture<String> cf = services.get(i).retrieveAsync(messages.get(i))
                    // Convert failure into null so the overall pipeline never fails
                    .exceptionally(ex -> null);
            guarded.add(cf);
        }

        return CompletableFuture.allOf(guarded.toArray(new CompletableFuture[0]))
                .thenApply(v -> guarded.stream()
                        .map(CompletableFuture::join) // never throws (we turned failures into null)
                        .filter(r -> r != null) // keep only successful results
                        .collect(Collectors.toList()));
    }

    /**
     * Task C — Fail-Soft (Fallback Policy)
     * Failures are replaced with fallbackValue; the returned future never fails.
     */
    public CompletableFuture<String> processAsyncFailSoft(
            List<Microservice> services,
            List<String> messages,
            String fallbackValue) {
        if (services.size() != messages.size()) {
            return CompletableFuture.completedFuture("");
        }

        List<CompletableFuture<String>> guarded = new ArrayList<>();
        for (int i = 0; i < services.size(); i++) {
            CompletableFuture<String> cf = services.get(i).retrieveAsync(messages.get(i))
                    .exceptionally(ex -> fallbackValue);
            guarded.add(cf);
        }

        return CompletableFuture.allOf(guarded.toArray(new CompletableFuture[0]))
                .thenApply(v -> guarded.stream()
                        .map(CompletableFuture::join) // never throws (failures became fallback)
                        .collect(Collectors.joining(" ")));
    }
}
