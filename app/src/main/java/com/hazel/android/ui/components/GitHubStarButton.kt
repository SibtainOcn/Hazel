package com.hazel.android.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R
import com.hazel.android.download.NetworkProxy.throughProxy
import com.hazel.android.ui.theme.sheetButtonColor
import com.hazel.android.update.HazelUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val REPO_URL = "https://github.com/${HazelUpdater.REPO_NAME}"

/**
 * The repository's star count: kept on the phone and asked of GitHub at most every 12 hours,
 * only while a screen showing it is open. Same GitHub API as the engine updates.
 */
object GitHubStars {
    private const val PREFS = "github_stars"
    private const val KEY_COUNT = "count"
    private const val KEY_AT = "at"
    private const val MAX_AGE_MS = 12 * 60 * 60 * 1000L

    @Volatile private var memory: Int? = null
    @Volatile private var memoryAt = 0L

    private val client by lazy {
        OkHttpClient.Builder()
            .throughProxy()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** The last known count without touching the disk or network, for the first frame. */
    fun cached(): Int? = memory

    /**
     * The saved count, refreshed from GitHub first when it is older than 12 hours. Never throws:
     * any failure (no network, rate limit, odd reply, unreadable saved value) keeps the last
     * count, or null, and the button shows just the GitHub mark.
     */
    suspend fun load(context: Context): Int? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (memory == null && prefs.contains(KEY_COUNT)) {
                memory = prefs.getInt(KEY_COUNT, -1).takeIf { it >= 0 }
                memoryAt = prefs.getLong(KEY_AT, 0L)
            }
            val now = System.currentTimeMillis()
            if (memory != null && now - memoryAt in 0 until MAX_AGE_MS) return@withContext memory
            fetch()?.let { fresh ->
                memory = fresh
                memoryAt = now
                prefs.edit().putInt(KEY_COUNT, fresh).putLong(KEY_AT, now).apply()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        memory
    }

    private fun fetch(): Int? = runCatching {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${HazelUpdater.REPO_NAME}")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Hazel-App")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null
            else JSONObject(response.body.string()).optInt("stargazers_count", -1).takeIf { it >= 0 }
        }
    }.getOrNull()
}

/** 204, 1.2k, 3.4M: at most six characters, so the button never grows past its slot. */
private fun shortCount(n: Int): String = when {
    n < 1000 -> n.toString()
    n < 999_950 -> "%.1f".format(java.util.Locale.ROOT, n / 1000.0).removeSuffix(".0") + "k"
    else -> "%.1f".format(java.util.Locale.ROOT, n / 1_000_000.0).removeSuffix(".0") + "M"
}

/**
 * The GitHub mark, an outlined star and the star count, as on the website. [onClick] opens the
 * repository in the app's page viewer, so Back returns here; without it the browser opens it.
 */
@Composable
fun GitHubStarButton(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    var stars by remember { mutableStateOf(GitHubStars.cached()) }
    LaunchedEffect(Unit) { stars = GitHubStars.load(context.applicationContext) }
    val label = stars?.let { pluralStringResource(R.plurals.github_stars_desc, it, it) }
        ?: stringResource(R.string.github_open_desc)
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(sheetButtonColor)
            .clickable(role = Role.Button) { onClick?.invoke() ?: openRepo(context) }
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        val ink = MaterialTheme.colorScheme.onSurface
        Icon(painterResource(R.drawable.ic_github), null, tint = ink, modifier = Modifier.size(20.dp))
        stars?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(
                    painterResource(R.drawable.ic_star_outline), null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Text(shortCount(it), color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

private fun openRepo(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, R.string.sheet_link_open_failed, Toast.LENGTH_SHORT).show()
    }
}
