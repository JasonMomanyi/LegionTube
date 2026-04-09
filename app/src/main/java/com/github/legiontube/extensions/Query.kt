package com.github.legiontube.extensions

fun query(block: () -> Unit) {
    Thread {
        try {
            block.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }.start()
}
