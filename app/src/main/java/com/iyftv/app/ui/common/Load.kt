package com.iyftv.app.ui.common

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

fun Throwable.userMessage(): String = message ?: javaClass.simpleName
