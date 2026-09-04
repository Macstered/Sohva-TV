package com.streammate.tv.feature.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Emits every [periodMillis], with the wait running off the Compose frame clock.
 *
 * The obvious way to drive a periodic UI update is
 *
 *     LaunchedEffect(Unit) { while (true) { delay(period); now = currentTimeMillis() } }
 *
 * but that makes the screen untestable. A Compose test clock runs with
 * `autoAdvance` on, so `waitForIdle` advances virtual time until the
 * composition settles. Virtual time reaching the next tick resumes the loop,
 * the loop writes state, the write schedules a recomposition, and the clock can
 * advance again — so idle is never reached and every subsequent test operation
 * blocks forever rather than failing.
 *
 * Putting the delay upstream of [flowOn] runs it on the real [Dispatchers.Default]
 * scheduler instead of the virtual clock, so advancing test time no longer
 * manufactures work and the composition can go idle between ticks. The
 * collector still resumes on the collecting coroutine's dispatcher, which for a
 * `LaunchedEffect` is the main thread — so main-thread-confined objects such as
 * a `MediaController` remain safe to touch in the collector body.
 *
 * @param emitImmediately emit once on collection before the first wait, for
 *   callers that previously read their source at the top of the loop body.
 */
fun tickerFlow(periodMillis: Long, emitImmediately: Boolean = true): Flow<Unit> = flow {
    if (emitImmediately) emit(Unit)
    while (true) {
        delay(periodMillis)
        emit(Unit)
    }
}.flowOn(Dispatchers.Default)
