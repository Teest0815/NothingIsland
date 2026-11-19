package dev.nothingisland

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Neue Version suchen: fragt das GitHub-Release "latest" ab und vergleicht die Build-Nummer. */
object UpdateCheck {
    private const val API = "https://api.github.com/repos/Teest0815/NothingIsland/releases/latest"

    data class Info(val build: Int, val name: String, val apkUrl: String, val pageUrl: String)

    sealed interface Result {
        data class Newer(val info: Info) : Result
        data object UpToDate : Result
        data object Failed : Result
    }

    /** Blockiert (Netzwerk): nur im Hintergrund aufrufen. */
    fun fetch(ctx: Context): Result = runCatching {
        val current = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode != 200) return@runCatching Result.Failed
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val name = o.optString("name")
            val build = Regex("""v\d+\.(\d+)""").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: return@runCatching Result.Failed
            val assets = o.optJSONArray("assets")
            var apk = ""
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == "NothingIsland.apk") apk = a.optString("browser_download_url")
            }
            val page = o.optString("html_url")
            if (build > current) Result.Newer(Info(build, name, apk.ifEmpty { page }, page)) else Result.UpToDate
        } finally {
            c.disconnect()
        }
    }.getOrDefault(Result.Failed)
}
