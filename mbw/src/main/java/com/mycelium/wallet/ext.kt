package com.mycelium.wallet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

inline fun startCoroutineTimer(
    scope: CoroutineScope,
    delayMillis: Long = 0,
    repeatMillis: Long = 0,
    crossinline action: (Int) -> Unit
) = scope.launch {
    delay(delayMillis)
    var counter = 0
    if (repeatMillis > 0) {
        while (true) {
            action(counter++)
            delay(repeatMillis)
        }
    } else {
        action(counter)
    }
}

fun <E> List<E>.randomOrNull(): E? = if (size > 0) random() else null

/**
 * Updates the [MutableStateFlow.value] atomically using the specified [function] of its value.
 *
 * [function] may be evaluated multiple times, if [value] is being concurrently updated.
 */
inline fun <T> MutableStateFlow<T>.update(function: (T) -> T) {
    while (true) {
        val prevValue = value
        val nextValue = function(prevValue)
        if (compareAndSet(prevValue, nextValue)) {
            return
        }
    }
}