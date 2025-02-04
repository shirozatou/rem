# rem

Remember any type of object with ViewModel.

## Description

No Parcelable, just in-memory. (Why not?)

## Dependency

`implementation("io.github.shirozatou:rem:1.0.1")`

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

### Clean up when ViewModel is cleared?

Currently only instance of `Clearable` is allowed, `Clearable#onCleared` is called after
`ViewModel#onCleared`.

For lazy people:

```kotlin
val foo = rememberWithViewModel {
    object : Clearable {
        val data = Any()
        override fun onCleared() {
            // TODO // Executed on main thread
        }
    }
}.data
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

### Reminder

Similar to `ViewModel`, don't leak `Context` or something.
