package com.example.pulserds_prog7313.utils

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A LiveData that delivers each value exactly once to exactly one observer.
 * Both setValue() (main thread) and postValue() (any thread) set the pending
 * flag so the event is never replayed on resume or to a second observer.
 */
class SingleLiveEvent<T> : MutableLiveData<T>() {

    // -- Pending flag ----------------------------------------------------------
    // AtomicBoolean ensures thread-safe read-and-clear in compareAndSet()
    private val pending = AtomicBoolean(false)

    // -- Observer --------------------------------------------------------------
    // Wraps the caller's observer so it only fires when a new value is pending
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        super.observe(owner) { value ->
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(value)
            }
        }
    }

    // -- setValue / postValue --------------------------------------------------
    // Both set the pending flag before delegating to MutableLiveData,
    // ensuring the event is consumed exactly once regardless of which
    // thread posts the value.
    override fun setValue(value: T) {
        pending.set(true)
        super.setValue(value)
    }

    override fun postValue(value: T) {
        pending.set(true)
        super.postValue(value)
    }
}