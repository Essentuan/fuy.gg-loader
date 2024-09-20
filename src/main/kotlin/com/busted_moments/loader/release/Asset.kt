package com.busted_moments.loader.release

import java.net.URL

@JvmRecord
data class Asset(
    val id: Long,
    val name: String,
    val size: Long,
    val downloads: Int,
    val createdAt: Long,
    val download: URL
)