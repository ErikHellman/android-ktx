/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.arch.lifecycle

import android.arch.lifecycle.Lifecycle.Event.ON_DESTROY
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.OnLifecycleEvent
import android.os.AsyncTask
import android.os.AsyncTask.Status.FINISHED
import android.support.annotation.MainThread

/**
 * The result from a call to <code>load()<code>.
 */
interface DeferredResult<out T> {
    infix fun then(resultAction: (result: T) -> Unit)
    fun cancel()
}

internal class LifecycleAwareAsyncTask<Result>(private val loader: () -> Result) :
    AsyncTask<Void, Void, Result>(), DeferredResult<Result>, LifecycleObserver {
    private var resultAction: ((result: Result) -> Unit)? = null

    override fun doInBackground(vararg params: Void?): Result {
        return loader()
    }

    override fun onPostExecute(result: Result) {
        super.onPostExecute(result)
        resultAction?.let { it(result) }
    }

    @OnLifecycleEvent(ON_DESTROY)
    override fun cancel() {
        if (!this.isCancelled && status != FINISHED) {
            super.cancel(false)
        }
    }

    @MainThread
    override infix fun then(resultAction: (result: Result) -> Unit) {
        if (status == FINISHED && !isCancelled) {
            val result = get()
            resultAction(result)
        } else {
            this.resultAction = resultAction
        }
    }
}

/**
 * Calls the <code>loader()</code> lambda using an AsyncTask and returns a
 * <code>DeferredResult</code>. The task is automatically cancelled at <code>onDestroy()</code>
 * using a <code>LifecycleObserver</code>.
 *
 * Use this to perform loading of data of the main thread like this:
 * <code>
 * load { expensiveNetworkCall() } then { displayResult(it) }
 * </code>
 *
 * @see DeferredResult
 * @see LifecycleObserver
 */
fun <T> LifecycleOwner.load(loader: () -> T): DeferredResult<T> {
    val task = LifecycleAwareAsyncTask(loader)
    lifecycle.addObserver(task)
    task.execute()
    return task
}
