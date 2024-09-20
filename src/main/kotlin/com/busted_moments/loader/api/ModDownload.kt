package com.busted_moments.loader.api

import java.net.URL

interface ModDownload {
    val id: String
    val version: String
    val fileName: String

    val url: URL
    val hash: String?
}