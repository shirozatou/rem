package io.github.shirozatou.rem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * remember any value like [remember] does,
 * but it use [ViewModel] to retain value during configuration changes.
 *
 * If [Clearable] is returned, [Clearable.onCleared] is called for clean up.
 *
 * ```
 * var flag by remember { mutableStateOf(true) }
 * if (flag) {
 *     val foo = rememberWithViewModel {
 *         object : Clearable {
 *             val data = Any()
 *             override fun onCleared() {
 *                 // Called on main thread once flag == false
 *             }
 *         }
 *     }.data
 * }
 * ```
 */
@Composable
fun <T : Any> rememberWithViewModel(
    key1: Any? = null,
    init: () -> T
): T {
    val vm = viewModel<HolderViewModel>(factory = HolderViewModel.Factory)
    val entryKey = currentCompositeKeyHash

    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember {
        val entry = vm.readOrRemember<T>(entryKey, init)
        Holder(entry, vm, lifecycleOwner, entryKey, key1)
    }
    val value = holder.getRememberedValue(vm, entryKey, key1) ?: init()
    SideEffect {
        holder.update(value, vm, lifecycleOwner, entryKey, key1)
    }
    return value
}

private class Holder<T>(
    private var remembered: HolderViewModel.Entry<T>,
    private var vm: HolderViewModel,
    private var lifecycleOwner: LifecycleOwner,
    private var k: Any,
    private var key1: Any?,
) : RememberObserver {

    override fun onRemembered() {}

    override fun onForgotten() {
        if (lifecycleOwner.lifecycle.currentState >= Lifecycle.State.RESUMED) {
            vm.forget(k)
        }
    }

    override fun onAbandoned() {
        onForgotten()
    }

    fun getRememberedValue(vm: HolderViewModel, entryKey: Any, key1: Any?): T? {
        return if (vm !== this.vm || entryKey != this.k || key1 != this.key1) null else remembered.value
    }

    fun update(
        value: T,
        vm: HolderViewModel,
        lifecycleOwner: LifecycleOwner,
        entryKey: Any,
        key1: Any?,
    ) {
        val f = vm !== this.vm || entryKey != this.k || key1 != this.key1
        if (f) {
            this.vm.forget(this.k)
        }
        this.lifecycleOwner = lifecycleOwner
        this.vm = vm
        this.k = entryKey
        this.key1 = key1

        if (f || value != remembered.value) {
            remembered = vm.remember(entryKey, value)
        }
    }
}

/**
 * ```
 * ScopedViewModelStore {
 *     val v1 = viewModel<YourViewModel>()
 *     val v2 = viewModel<YourViewModel>()
 *
 *     ScopedViewModelStore {
 *         val v3 = viewModel<YourViewModel>()
 *
 *         assert(v1 === v2)
 *         assert(v1 !== v3)
 *     }
 * }
 * ```
 */
@Composable
fun ScopedViewModelStore(content: @Composable () -> Unit) {
    val viewModelStoreOwner = rememberWithViewModel {
        object : ViewModelStoreOwner, Clearable {
            override val viewModelStore: ViewModelStore = ViewModelStore()
            override fun onCleared() {
                viewModelStore.clear()
            }
        }
    }
    CompositionLocalProvider(
        LocalViewModelStoreOwner provides viewModelStoreOwner,
        content = content
    )
}
