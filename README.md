# rem

Remember any type of object with ViewModel. No Parcelable, just in-memory. (Why not?)

## Dependency

`implementation("io.github.shirozatou:rem:1.0.3")`

## Usage

```kotlin
remember {
    Any() // ❌ Evaluated again when screen rotated
}
rememberSaveable {
    Any() // ❌ IllegalArgumentException. Cannot be stored to Bundle (Android)
}
rememberWithViewModel {
    Any() // 🥳 Restore from ViewModel when screen rotated
}
```

Remembered value is forget when
- once goes out of scope
- key changed
- viewModel in current scope is cleared

### Clean up

Currently only instance of `Clearable` is allowed.
```kotlin
var flag by remember { mutableStateOf(true) }
if (flag) {
    val foo = rememberWithViewModel(key) {
        object : Clearable {
            val data = Any()
            override fun onCleared() {
                // Called on main thread once flag == false
            }
        }
    }.data
}
```

### Can I remember ViewModel with rememberWithViewModel?

ViewModel should be managed by `ViewModelStoreOwner`

For scoped ViewModel, use `ScopedViewModelStore`:

```kotlin
ScopedViewModelStore {
    val v1 = viewModel<YourViewModel>()
    val v2 = viewModel<YourViewModel>()

    ScopedViewModelStore {
        val v3 = viewModel<YourViewModel>()

        assert(v1 === v2)
        assert(v1 !== v3)
    }
}
```
ScopedViewModelStore is a simple trick btw.
```kotlin
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
```

### Solution for LazyList
```kotlin
ScopedSaveableStateRegistry {
    LazyColumn {
        items(count = 100) {
            val remembered = rememberSaveable {
                Any() // You can remember any type of object here
            }
            val notRemembered = rememberWithViewModel {
                Any()
            }
            Text(text = remembered.toString())
            Text(text = notRemembered.toString())
        }
    }
}
```

### Reminder

Similar to `ViewModel`, don't leak `Context` or something.
