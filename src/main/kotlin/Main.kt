import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

fun main() {
    val url = URI.create("http://127.0.0.1:8080/").toURL()
    try {
        val (fileBytes, complete) = downloadFullFile(url)
        println("Downloaded ${fileBytes.size} bytes.")
        println("SHA-256: ${sha256(fileBytes)}")
        println("Complete: $complete")
    } catch (e: IOException) {
        println("Error: Could not connect to server — ${e.message}")
    } catch (e: Exception) {
        println("Unexpected error: ${e.message}")
    }
}

/**
 * Downloads an entire file from a potentially unreliable HTTP server using range requests.
 * Performs integrity checks using Content-Length when available.
 *
 * @param url The target URL to download the file from.
 *
 * @return A pair:
 *   - First: Full byte array of the downloaded file.
 *   - Second: Boolean indicating whether the download is believed to be complete.
 *
 * @param requestRetries Number of retry attempts for each HTTP request in case of failure.
 *
 * @throws IOException If repeated download attempts fail due to I/O errors.
 */
fun downloadFullFile(url: URL, requestRetries: Int = 3): Pair<ByteArray, Boolean> {
    val buffer = ByteArrayOutputStream()

    // First request to get the initial chunk and determine total size from Content-Length
    val (firstChunk, contentLength) = downloadRange(url, 0, null, maxRetries = requestRetries)
    val declaredSize = contentLength?.let { firstChunk.size + it }
    buffer.write(firstChunk)
    var downloaded = firstChunk.size

    while (true) {
        val (part, remaining) = downloadRange(url, downloaded, declaredSize, maxRetries = requestRetries)

        if (part.isNotEmpty()) {
            buffer.write(part)
            downloaded += part.size
        }

        // If we know the declared size, the server says no more data is available, and we downloaded exactly what was expected
        if (declaredSize != null && remaining != null && remaining == 0 && downloaded == declaredSize) {
            println("Download complete: received all $declaredSize bytes.")
            return buffer.toByteArray() to true
        }

        // Otherwise download while we can
        if (part.isEmpty()) {
            if (declaredSize != null && downloaded < declaredSize) {
                println("Warning: received 0 bytes at offset $downloaded, but expected $declaredSize — possible incomplete file!")
                return buffer.toByteArray() to false
            } else {
                println("Received 0 bytes at offset $downloaded — assuming end of file")
                break
            }
        }
    }

    return buffer.toByteArray() to (declaredSize == null)
}

/**
 * Attempts to download a range of bytes from the given URL, retrying up to [maxRetries] times on failure.
 *
 * @param url The target URL to download from.
 * @param start The byte offset to start downloading from.
 * @param declaredSize The expected total size of the file, if known.
 * @param maxRetries Maximum number of retries in case of I/O failures.
 *
 * @return A pair:
 *   - First: Byte array with downloaded data.
 *   - Second: Estimated number of bytes remaining according to Content-Length (maybe null).
 *
 * @throws IOException If all retry attempts fail due to I/O error.
 */
fun downloadRange(url: URL, start: Int, declaredSize: Int?, maxRetries: Int = 3): Pair<ByteArray, Int?> {
    var lastError: IOException? = null
    repeat(maxRetries) { attempt ->
        try {
            val conn = url.openConnection() as HttpURLConnection
            if (start > 0) {
                conn.setRequestProperty("Range", "bytes=$start-")
            }
            conn.requestMethod = "GET"

            val contentLength = conn.getHeaderField("Content-Length")?.toIntOrNull()
            val bytes = conn.inputStream.readAllBytes()

            val totalSize = if (start == 0) contentLength else declaredSize
            val newTotal = start + bytes.size

            if (bytes.isNotEmpty()) {
                println("Received ${bytes.size} bytes (${totalSize?.let { "$newTotal/$it" } ?: "$newTotal/?"})" +
                        totalSize?.let { " (${100 * newTotal / it}%)" }.orEmpty())
            }

            val fileRemained = contentLength?.minus(bytes.size)
            return bytes to fileRemained
        } catch (e: IOException) {
            lastError = e
            println("Retry ${attempt + 1}/$maxRetries failed at offset $start: ${e.message}")
        }
    }
    throw IOException("Failed to download at offset $start after $maxRetries attempts", lastError!!)
}

// Computes SHA-256 hash as a hex string
fun sha256(data: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(data)
    return digest.joinToString("") { "%02x".format(it) }
}
