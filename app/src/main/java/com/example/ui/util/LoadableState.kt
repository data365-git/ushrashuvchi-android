package com.example.ui.util

sealed class LoadableState<out T> {
    object Loading : LoadableState<Nothing>()
    data class Success<T>(val value: T) : LoadableState<T>()
    data class Empty(val message: String? = null) : LoadableState<Nothing>()
    data class Error(val message: String) : LoadableState<Nothing>()
}
