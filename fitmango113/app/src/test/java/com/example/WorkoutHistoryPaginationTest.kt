package com.example

import com.example.health.WorkoutReadSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkoutHistoryPaginationTest {
    @Test fun android13EmptyFinalTokenEndsReadWithoutFetchingFirstPageAgain() = runBlocking {
        val requests = mutableListOf<String?>()
        val records = WorkoutReadSupport.readPages<Int> { token ->
            requests += token
            if (token == null) listOf(1, 2) to "next" else listOf(3) to ""
        }
        assertEquals(listOf(null, "next"), requests)
        assertEquals(listOf(1, 2, 3), records)
    }
    @Test fun nullAndBlankFinalTokensAreBothAcceptedForEmptyOrNonemptyHistory() = runBlocking {
        for (end in listOf(null, "", " ")) {
            var calls = 0
            assertEquals(listOf(4), WorkoutReadSupport.readPages<Int> { calls++; listOf(4) to end })
            assertEquals(1, calls)
            assertTrue(WorkoutReadSupport.readPages<Int> { emptyList<Int>() to end }.isEmpty())
        }
    }
    @Test fun genuineTokenCycleIsRejectedWithoutReturningPartialData() = runBlocking {
        var calls = 0
        val result = runCatching { WorkoutReadSupport.readPages<Int> { calls++; listOf(calls) to "same" } }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(2, calls)
    }
    @Test fun providerFailureAndCancellationArePropagated() = runBlocking {
        val failure = IllegalStateException("provider unavailable")
        assertSame(failure, runCatching { WorkoutReadSupport.readPages<Int> { throw failure } }.exceptionOrNull())
        val cancelled = CancellationException("screen closed")
        assertSame(cancelled, runCatching { WorkoutReadSupport.readPages<Int> { throw cancelled } }.exceptionOrNull())
    }
    @Test fun recordAndPageLimitsPreventAnUnboundedRead() = runBlocking {
        assertTrue(runCatching { WorkoutReadSupport.readPages<Int> { List(2001) { it } to null } }.isFailure)
        var calls = 0
        assertTrue(runCatching { WorkoutReadSupport.readPages<Int> { emptyList<Int>() to (++calls).toString() } }.isFailure)
        assertEquals(25, calls)
    }
}
