//  Copyright © 2016 Compass. All rights reserved.

package com.compass.snail

import com.compass.snail.disposer.Disposable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.Semaphore
import kotlin.math.max

open class Observable<T> : IObservable<T> {
    private var isStopped = 0
    private var stoppedEvent: Event<T>? = null
    private var subscribers: MutableList<Subscriber<T>> = mutableListOf()

    override fun subscribe(dispatcher: CoroutineDispatcher?, next: ((T) -> Unit)?, error: ((Throwable) -> Unit)?, done: (() -> Unit)?): Disposable {
        val subscriber = Subscriber(dispatcher, createHandler(next, error, done), this)
        stoppedEvent?.let {
            notify(subscriber, it)
            return subscriber
        }
        subscribers.add(subscriber)
        return subscriber
    }

    override fun on(dispatcher: CoroutineDispatcher): Observable<T> {
        val observable = Observable<T>()
        subscribe(dispatcher, { observable.next(it) }, { observable.error(it) }, { observable.done() })
        return observable
    }

    override fun next(value: T) {
        if (isStopped == 0) {
            on(Event(next = Next(value)))
        }
    }

    override fun error(error: Throwable) {
        on(Event(error = error))
    }

    override fun done() {
        on(Event(done = true))
    }

    override fun removeSubscribers() {
        subscribers.clear()
    }

    override fun removeSubscriber(subscriber: Disposable) {
        subscribers.remove(subscriber)
    }

    private fun on(event: Event<T>) {
        if (stoppedEvent != null) return

        event.next?.let {
            subscribers.forEach { notify(it, event) }
        }
        event.error?.let {
            subscribers.forEach { notify(it, event) }
            stoppedEvent = event
        }
        event.done?.let {
            subscribers.forEach { notify(it, event) }
            stoppedEvent = event
        }
    }

    protected fun createHandler(next: ((T) -> Unit)? = null, error: ((Throwable) -> Unit)? = null, done: (() -> Unit)? = null): (Event<T>) -> Unit {
        return { event ->
            event.next?.let { next?.invoke(it.value) }
            event.error?.let { error?.invoke(it) }
            event.done?.let { done?.invoke() }
        }
    }

    fun notify(subscriber: Subscriber<T>, event: Event<T>) {
        subscriber.dispatcher?.let {
            GlobalScope.launch(it) {
                safeNotify(subscriber, event)
            }
            return
        }

        safeNotify(subscriber, event)
    }

    private fun safeNotify(subscriber: Subscriber<T>, event: Event<T>) {
        try {
            subscriber.eventHandler(event)
        } catch (e: Exception) {
            e.printStackTrace()
            subscribers.remove(subscriber)
        }
    }

    override suspend fun block(): BlockResult<T> {
        var result: T? = null
        var error: Throwable? = null
        val semaphore = Semaphore(0)

        subscribe(next = {
            result = it
            semaphore.release()
        }, error = {
            error = it
            semaphore.release()
        }, done = {
            semaphore.release()
        })

        semaphore.acquire()

        return BlockResult(result, error)
    }

    override fun throttle(delayMs: Long): Observable<T> {
        val observable = Observable<T>()
        val scheduler = Scheduler(delayMs)
        scheduler.start()

        var next: T? = null
        scheduler.event.subscribe(next = {
            next?.let {
                observable.next(it)
                next = null
            }
        })

        subscribe(next = { next = it }, error = { observable.error(it) }, done = { observable.done() })
        return observable
    }

    override fun debounce(delayMs: Long): Observable<T> {
        val observable = Observable<T>()
        val scheduler = Scheduler(delayMs)

        var next: T? = null
        scheduler.event.subscribe(next = {
            next?.let {
                observable.next(it)
                next = null
            }
        })

        subscribe(next = {
            next = it
            scheduler.start()
        }, error = { observable.error(it) }, done = { observable.done() })
        return observable
    }

    override fun skip(first: Int): Observable<T> {
        val observable = Observable<T>()
        var count = first
        subscribe(next = {
            if (count == 0) {
                observable.next(it)
            } else if (count < 0) {
                observable.error(IllegalArgumentException("Parameter value must be nonzero"))
            }
            count = max(count - 1, 0)
        }, error = {
            observable.error(it)
        }, done = {
            observable.done()
        })

        return observable
    }
}
