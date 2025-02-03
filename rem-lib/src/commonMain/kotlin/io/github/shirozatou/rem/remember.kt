package io.github.shirozatou.rem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun <T : Any> rememberWithViewModel(
    key1: Any? = null,
    init: () -> T
): T {
    val vm = viewModel<HolderViewModel>(factory = HolderViewModel.Factory)
//    val entryKey = rememberSaveable { vm.nextKey }
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
