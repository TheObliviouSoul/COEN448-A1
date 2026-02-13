Failure Semantics in Concurrent Systems

Introduction
In concurrent systems, multiple tasks or microservices execute in parallel. While concurrency improves performance and scalability, it also introduces complexity when failures occur. If one concurrent task fails, the system must explicitly define how that failure affects the overall computation. These rules are known as **failure semantics**.

This document describes three explicit failure-handling policies implemented in this assignment:
- Fail-Fast (Atomic)
- Fail-Partial (Best-Effort)
- Fail-Soft (Fallback)

Each policy represents a different trade-off between correctness, availability, and robustness.

---

Fail-Fast (Atomic Policy)

Description
The Fail-Fast policy treats the concurrent operation as **atomic**. All microservices are invoked concurrently, but **if any single microservice fails**, the entire computation fails immediately. No partial result is returned, and the exception is propagated to the caller.

Behavior
- All services start concurrently
- Any failure causes the aggregate future to complete exceptionally
- No successful results are returned

When to Use
Fail-Fast is appropriate for **correctness-critical systems**, where partial results are invalid or dangerous.

Example
- Financial transactions
- Payment processing
- Distributed database commits

In these systems, returning incomplete or inconsistent data is worse than returning no result at all.

Risks
- Reduced availability
- A single failure can prevent useful partial progress

---

Fail-Partial (Best-Effort Policy)

Description
The Fail-Partial policy allows the computation to complete even if some microservices fail. Each service handles its failure independently. Successful results are returned, while failed invocations are ignored or omitted. The overall computation **never fails**.

Behavior
- All services start concurrently
- Failures are handled per service
- Only successful results are collected
- No exception propagates to the caller

When to Use
Fail-Partial is useful when **partial information is still valuable**.

Example
- Dashboards
- Analytics pipelines
- Log aggregation systems

In these cases, missing some data points is acceptable and preferable to no result.

Risks
- Results may be incomplete
- Callers must understand that missing data indicates failures

---

Fail-Soft (Fallback Policy)

Description
The Fail-Soft policy replaces failures with a **predefined fallback value**. The computation always completes normally, regardless of how many services fail. This maximizes availability but may hide underlying problems.

Behavior
- All services start concurrently
- Failed services return a fallback value
- The aggregate future always completes successfully

When to Use
Fail-Soft is appropriate for **high-availability systems** where degraded output is acceptable.

Example
- Recommendation systems
- Search result ranking
- UI personalization services

If some components fail, the system still provides a usable (though degraded) response.

Risks of Masking Failures
Fail-Soft policies can **hide serious errors**:
- Failures may go unnoticed
- Incorrect fallback data may be mistaken for real data
- Long-term system health may degrade without alerts

Because of this, Fail-Soft policies must be carefully documented and monitored.

---

## Comparison of Failure Policies

| Policy       | Fails Entire Operation | Returns Partial Results | Uses Fallback | Typical Use Case |
|--------------|------------------------|-------------------------|---------------|------------------|
| Fail-Fast    | Yes                    | No                      | No            | Transactions, payments |
| Fail-Partial | No                     | Yes                     | No            | Dashboards, analytics |
| Fail-Soft    | No                     | Yes (with fallback)     | Yes           | High-availability services |

---

Conclusion
Concurrency itself is not the primary challenge in distributed systems. The real challenge lies in defining **clear and intentional failure semantics**. By explicitly choosing between Fail-Fast, Fail-Partial, and Fail-Soft policies, system designers can make informed trade-offs between correctness, availability, and resilience.

Failure semantics should never be implicit — they must be explicitly designed, implemented, tested, and documented.
