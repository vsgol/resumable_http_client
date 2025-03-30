# Resumable File Downloader

A robust Kotlin-based client for downloading files from a glitch HTTP server that supports `Range` requests. It handles partial responses, verifies data integrity via `Content-Length` and `SHA-256` (printed in console for manual verification), and retries failed requests.

## Usage

```bash
./gradlew run
```

The program will:

- connect to the server;
- download the file in chunks;
- verify integrity via `Content-Length`;
- print whether the full file was successfully downloaded.

---

## Example Output

```
Received 65564 bytes (951959/952064) (99%)
Received 105 bytes (952064/952064) (100%)
Download complete: received all 952064 bytes.
Downloaded 952064 bytes.
SHA-256: a62c9f58184aa02855c282d74998401964a29e3873d702b4b827df2fc7c8a41e
Complete: true
```

---

## Future Improvements

- **Write to file instead of memory**\
  Current implementation stores data in memory (`ByteArrayOutputStream`), which would be inefficient for large files. Better replace it with `FileOutputStream` and write directly in file.

- **Log with timestamps**\
  Replace all `println(...)` with a `log(...)` with timestamps, e.g.:

- **Human-readable byte output**\
  Format sizes (e.g. downloaded, total) in KB or MB instead of bytes for better readability.
