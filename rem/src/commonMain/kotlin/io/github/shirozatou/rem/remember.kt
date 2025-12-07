package io.github.shirozatou.rem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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
    val entryKey = rememberSaveable {
        vm.nextKey
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember {
        Holder<T>(
            vm = vm,
            lifecycleOwner = lifecycleOwner,
            k = entryKey,
            key1 = key1
        )
    }
    val v = holder.getValueAtComposition(
        currentViewModel = vm,
        currentEntryKey = entryKey,
        currentKey1 = key1,
        currentLifecycleOwner = lifecycleOwner,
        init = init
    )
    SideEffect {
        holder.onSideEffect(
            currentViewModel = vm,
            currentEntryKey = entryKey,
            currentKey1 = key1,
            currentLifecycleOwner = lifecycleOwner,
            value = v,
        )
    }
    return v
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

@OptIn(ExperimentalAtomicApi::class)
private class Holder<T : Any>(
    private var vm: HolderViewModel,
    private var lifecycleOwner: LifecycleOwner,
    private var k: Any,
    private var key1: Any?,
) : RememberObserver {

    private var remembered = vm.readValue<T>(k)

    // Composition is not thread-safe. Hold it until onSideEffect/onAbandoned called
    private var pending = AtomicReference<T?>(null)

    override fun onRemembered() {}

    override fun onForgotten() {
        pending.exchange(null)?.let(vm::dispose)
        if (lifecycleOwner.lifecycle.currentState >= Lifecycle.State.RESUMED) {
            remembered?.let { vm.forget(k, it) }
        }
    }

    override fun onAbandoned() {
        pending.exchange(null)?.let(vm::dispose)
    }

    // This can be called on any thread
    fun getValueAtComposition(
        currentViewModel: HolderViewModel,
        currentEntryKey: Any,
        currentKey1: Any?,
        currentLifecycleOwner: LifecycleOwner,
        init: () -> T,
    ): T {
        val value = pending.load() ?: remembered?.value
        val v = if (value == null ||
            currentViewModel !== vm ||
            currentEntryKey != k ||
            currentKey1 != key1
        ) {
            val new = init()
            val old = pending.exchange(new)
            if (old !== new) {
                old?.let(vm::dispose)
            }
            new
        } else {
            value
        }
        vm = currentViewModel
        k = currentEntryKey
        key1 = currentKey1
        lifecycleOwner = currentLifecycleOwner
        return v
    }

    fun onSideEffect(
        currentViewModel: HolderViewModel,
        currentEntryKey: Any,
        currentKey1: Any?,
        currentLifecycleOwner: LifecycleOwner,
        value: T,
    ) {
        vm = currentViewModel
        k = currentEntryKey
        key1 = currentKey1
        lifecycleOwner = currentLifecycleOwner
        if (remembered?.value !== value) {
            remembered = currentViewModel.remember(currentEntryKey, value)
        }
        // Skip if another recomposition triggered
        pending.compareAndSet(value, null)
    }
}
