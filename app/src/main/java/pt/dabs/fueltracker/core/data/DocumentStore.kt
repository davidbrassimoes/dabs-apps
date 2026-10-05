package pt.dabs.fueltracker.core.data

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Leitura e escrita de ficheiros escolhidos pelo utilizador (Storage Access Framework).
 * Os URIs circulam como texto para a camada de ViewModel não depender de classes Android.
 */
interface DocumentStore {
    suspend fun writeText(uri: String, text: String)
    suspend fun readText(uri: String): String
}

@Singleton
class ContentResolverDocumentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentStore {

    override suspend fun writeText(uri: String, text: String) = withContext(Dispatchers.IO) {
        // "wt" trunca o ficheiro existente; sem isso, um ficheiro maior ficaria com lixo no fim.
        val stream = context.contentResolver.openOutputStream(uri.toUri(), "wt") ?: throw IOException("cannot open $uri")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    override suspend fun readText(uri: String): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri.toUri()) ?: throw IOException("cannot open $uri")
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
