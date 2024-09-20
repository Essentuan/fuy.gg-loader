package com.busted_moments.loader.api

interface Dependency {
    val name: String

    suspend fun fetch(): List<Pair<String, ModDownload?>>
}