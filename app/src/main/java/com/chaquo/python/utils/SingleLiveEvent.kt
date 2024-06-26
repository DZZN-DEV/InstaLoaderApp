/*
 *  Copyright 2017 Google Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.chaquo.python.utils

import androidx.annotation.MainThread
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

class SingleLiveEvent<T> : MutableLiveData<T>() {

    private companion object {
        private const val TAG = "SingleLiveEvent"
    }

    private var mObserver: Observer<in T>? = null
    private var mObserverWrapper: Observer<T>? = null

    private val mPending = AtomicBoolean(false)

    @MainThread
    fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        if (mObserver != null) {
            throw IllegalStateException("Cannot register multiple observers on a SingleLiveEvent")
        }

        mObserver = observer
        mObserverWrapper = Observer { t ->
            if (mPending.compareAndSet(true, false)) {
                mObserver?.onChanged(t)
            }
        }
        super.observe(owner, mObserverWrapper!!)
    }

    override fun removeObserver(observer: Observer<in T>) {
        if (observer === mObserverWrapper || observer === mObserver) {
            super.removeObserver(mObserverWrapper!!)
            mObserver = mObserverWrapper = null
        }
    }

    @MainThread
    fun setValue(t: T?) {
        mPending.set(true)
        super.setValue(t)
    }

    /**
     * Used for cases where T is Void, to make calls cleaner.
     */
    @MainThread
    fun call() {
        setValue(null)
    }
}