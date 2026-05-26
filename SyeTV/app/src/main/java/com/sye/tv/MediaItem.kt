package com.sye.tv

import java.io.Serializable

data class MediaItem(
    val id: String,
    val title: String,
    val url: String
) : Serializable
