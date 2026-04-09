package com.github.legiontube.ui.extensions

import com.github.legiontube.api.obj.Comment

fun List<Comment>.filterNonEmptyComments(): List<Comment> {
    return filter { !it.commentText.isNullOrEmpty() }
}
