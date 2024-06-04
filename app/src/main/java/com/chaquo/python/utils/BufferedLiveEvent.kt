package com.chaquo.python.utils

import android.os.Handler
import androidx.annotation.MainThread
import androidx.annotation.NonNull
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import java.util.ArrayList

class BufferedLiveEvent<T> : SingleLiveEvent<T>() {

    private val mBuffer = ArrayList<T>()
    private var mHandler: Handler? = null

    @MainThread
    override fun postValue(@Nullable value: T?) {
        // Delay initialization for unit tests.
        if (mHandler == null) {
            mHandler = Handler(Looper.getMainLooper())
        }
        mHandler?.post {
            setValue(value)
        }
    }

    override fun setValue(@Nullable t: T?) {
        if (hasActiveObservers() && mBuffer.isEmpty()) {  // See onActive
            super.setValue(t)
        } else {
            mBuffer.add(t)
        }
    }

    override fun onActive() {
        // Don't use a foreach loop, an observer might call setValue and lengthen the buffer.
        for (i in 0 until mBuffer.size) {
            super.setValue(mBuffer[i])
        }
        mBuffer.clear()
    }
}

// Base class SingleLiveEvent
abstract class SingleLiveEvent<T> : LiveData<T>() {
    private val mPending = AtomicBoolean(false)

    @MainThread
    override fun observe(@NonNull owner: LifecycleOwner, @NonNull observer: Observer<in T>) {
        if (hasActiveObservers()) {
            // If we already have an observer, just call the onChanged() method directly.
            observer.onChanged(value)
        } else {
            super.observe(owner, Observer { t ->
                if (mPending.compareAndSet(true, false)) {
                    observer.onChanged(t)
                }
            })
            postValue(value)
        }
    }

    @MainThread
    override fun setValue(@Nullable t: T?) {
        mPending.set(true)
        super.setValue(t)
    }
}