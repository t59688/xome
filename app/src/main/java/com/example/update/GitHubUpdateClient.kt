package com.example.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Release metadata lookup shared by foreground periodic checks and manual checks. */
class GitHubUpdateClient {
    suspend fun latestRelease(channel: UpdateChannel = UpdateChannel.STABLE): GitHubRelease? =
        withContext(Dispatchers.IO) {
            val endpoint = if (channel == UpdateChannel.STABLE) "releases/latest"
                else "releases?per_page=30"
            val connection = URL("https://api.github.com/repos/${GitHubRelease.REPOSITORY}/$endpoint")
                .openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connection.setRequestProperty("User-Agent", "Xome-Android")
                when (connection.responseCode) {
                    404 -> null
                    200 -> {
                        val text = connection.inputStream.use { input ->
                            val out = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                if (out.size() + n > 4_194_304) throw IOException("更新信息过大")
                                out.write(buffer, 0, n)
                            }
                            out.toString("UTF-8")
                        }
                        if (channel == UpdateChannel.STABLE) {
                            GitHubRelease.parse(text, channel)
                                ?: throw IOException("最新版本缺少可安装的签名 APK")
                        } else {
                            val releases = JSONArray(text)
                            (0 until releases.length()).mapNotNull { index ->
                                releases.optJSONObject(index)?.let {
                                    GitHubRelease.parse(it.toString(), channel)
                                }
                            }.maxByOrNull { it.version }
                        }
                    }
                    403, 429 -> throw IOException("GitHub 访问受限，请稍后重试")
                    else -> throw IOException("GitHub 更新检查失败（HTTP ${connection.responseCode}）")
                }
            } finally {
                connection.disconnect()
            }
        }
}
