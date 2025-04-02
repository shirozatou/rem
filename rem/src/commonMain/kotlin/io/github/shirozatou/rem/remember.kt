package io.github.shirozatou.rem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Remember any value like [remember] does,
 * but it use [ViewModel] to retain value during configuration changes.
 *
 * For remember value inside LazyList(LazyColumn), see [ScopedSaveableStateRegistry].
 *
 * If returned object is [Clearable], [Clearable.onCleared] will be called on dispose.
 *
 * Sample:
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
        val entry = vm.readOrRemember(entryKey, init)
        Holder(entry, vm, lifecycleOwner, entryKey, key1)
    }
    val value = holder.getRememberedValue(vm, entryKey, key1) ?: init()
    SideEffect {
        holder.update(value, vm, lifecycleOwner, entryKey, key1)
    }
    return value
}

/**
 * Sample:
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

/**
 * Scope that keep saved state with ViewModel.
 *
 * *Workaround for LazyList currently.*
 *
 * Sample:
 * ```
 * ScopedSaveableStateRegistry {
 *     LazyColumn {
 *         items(count = 100) {
 *             val remembered = rememberSaveable {
 *                 Any() // You can remember any type of object here
 *             }
 *             val notRemembered = rememberWithViewModel {
 *                 Any()
 *             }
 *             Text(text = remembered.toString())
 *             Text(text = notRemembered.toString())
 *         }
 *     }
 * }
 * ```
 */
@Composable
fun ScopedSaveableStateRegistry(content: @Composable () -> Unit) {
    val holder = rememberWithViewModel {
        object {
            var registry = SaveableStateRegistry(null) { true }
            fun onSaveState() {
                val saved = registry.performSave()
                registry = SaveableStateRegistry(saved) { true }
            }
        }
    }
    CompositionLocalProvider(
        value = LocalSaveableStateRegistry provides holder.registry,
        content = content,
    )
    DisposableEffect(holder) {
        onDispose(holder::onSaveState)
    }
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
