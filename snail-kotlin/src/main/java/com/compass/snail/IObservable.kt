//  Copyright © 2016 Compass. All rights reserved.

package com.compass.snail

import kotlinx.coroutines.CoroutineDispatcher

interface IObservable<T> {
    fun subscribe(dispatcher: CoroutineDispatcher? = null, next: ((T) -> Unit)? = null, error: ((Throwable) -> Unit)? = null, done: (() -> Unit)? = null): Subscriber<T>
    fun on(dispatcher: CoroutineDispatcher): Observable<T>
    fun next(value: T)
    fun error(error: Throwable)
    fun done()
    fun removeSubscribers()
    fun removeSubscriber(subscriber: Subscriber<T>)
    suspend fun block(): BlockResult<T>
    fun throttle(delayMs: Long): Observable<T>
    fun debounce(delayMs: Long): Observable<T>
    fun skip(first: Int): Observable<T>
}
