package com.busted_moments.loader.release

import com.busted_moments.loader.api.ModDownload
import java.net.URL

@JvmRecord
data class Release(
    val tag: String,
    val mc: String,
    val asset: Asset,
    override val hash: String
) : ModDownload {
    override val id: String
        get() = asset.name.split('-').first()

    override val version: String
        get() = tag
    override val fileName: String
        get() = asset.name
    override val url: URL
        get() = asset.download
}