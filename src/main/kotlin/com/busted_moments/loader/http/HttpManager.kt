package com.busted_moments.loader.http

import com.google.gson.GsonBuilder
import kotlinx.coroutines.future.asDeferred
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

internal val GSON = GsonBuilder().setPrettyPrinting().create()

internal object HttpManager {
    private val client = HttpClient.newHttpClient()

    suspend fun get(string: String): HttpResponse<String> {
        return get(URI.create(string))
    }

    suspend fun get(url: URL): HttpResponse<String> {
        return get(url.toURI())
    }

    suspend fun get(uri: URI): HttpResponse<String> {
        return client.sendAsync<String>(
            HttpRequest.newBuilder(uri)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        ).asDeferred().await()
    }
}

internal suspend inline fun <reified T: Any> fetch(url: String): T? {
    val response = HttpManager.get(url)
    if (response.statusCode() != 200)
        return null

    return GSON.fromJson<T>(response.body(), T::class.java)
}