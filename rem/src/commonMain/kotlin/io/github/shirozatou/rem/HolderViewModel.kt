package io.github.shirozatou.rem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.reflect.KClass

@OptIn(ExperimentalAtomicApi::class)
internal class HolderViewModel private constructor() : ViewModel() {

    companion object Factory : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
            if (modelClass == HolderViewModel::class)
                @Suppress("UNCHECKED_CAST")
                return HolderViewModel() as T
            else
                throw IllegalArgumentException()
        }
    }

    class Entry<out T>(val value: T)

    private val seq = AtomicInt(0)

    val nextKey: Int
        get() = seq.incrementAndFetch()

    private val map = mutableMapOf<Any, Entry<*>>()

    /**
     * Read is always thread safe.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> readValue(key: Any): Entry<T>? {
        return map[key] as? Entry<T>
    }

    /**
     * Remember new value and dispose old if it is not `value`.
     */
    fun <T> remember(key: Any, value: T): Entry<T> {
        val v = Entry(value)
        map.put(key, v)?.let { old ->
            if (old.value !== value) {
                dispose(old)
            }
        }
        return v
    }

    /**
     * Remove entry only if it is currently mapped.
     */
    fun forget(key: Any, entry: Entry<*>) {
        if (map[key] !== entry) {
            return
        }
        map.remove(key)?.dispose()
    }

    private fun Entry<*>.dispose() {
        dispose(value)
    }

    /**
     * Main scope for clear, don't cancel it.
     */
    private val mainScope = MainScope()

    override fun onCleared() {
        map.forEach {
            it.value.dispose()
        }
        map.clear()
    }

    // todo
    fun dispose(value: Any?) {
        // have a rest
        mainScope.launch {
            when (value) {
                is Clearable -> value.onCleared()
            }
        }
    }
}
