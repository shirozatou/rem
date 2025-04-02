package io.github.shirozatou.rem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

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

    @JvmInline
    value class Entry<out T>(val value: T)

    private val map = mutableMapOf<Any, Entry<*>>()


    @Suppress("UNCHECKED_CAST")
    fun <T> readValue(key: Any): Entry<T>? {
        return map[key] as? Entry<T>
    }

    fun <T> remember(key: Any, value: T): Entry<T> {
        val v = Entry(value)
        map.put(key, v)?.let { old ->
            if (old.value !== value) {
                dispose(old)
            }
        }
        return v
    }

    fun <T> readOrRemember(key: Any, supplier: () -> T): Entry<T> {
        return readValue(key) ?: remember(key, supplier())
    }

    fun forget(key: Any) {
        map.remove(key)?.let(::dispose)
    }

    private val mainScope = MainScope()

    private fun dispose(entry: Entry<*>) {
        // have a rest
        mainScope.launch {
            entry.onDispose()
        }
    }

    // todo
    private fun Entry<*>.onDispose() {
        when (value) {
            is Clearable -> value.onCleared()
        }
    }

    override fun onCleared() {
        map.forEach {
            dispose(it.value)
        }
        map.clear()
    }
}
