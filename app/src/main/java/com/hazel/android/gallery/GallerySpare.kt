package com.hazel.android.gallery

import android.content.Context
import android.util.Log
import com.hazel.android.HazelApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

/**
 * One gallery-dl process started ahead of time, with Python and gallery-dl already loaded, so
 * the next run skips that start-up. It waits for its arguments on stdin, runs once and exits;
 * a run never shares a process with another. Anything unusual and the run starts cold instead.
 */
internal object GallerySpare {

    private class Spare(val process: Process, val stderr: File, val key: String)

    private var spare: Spare? = null
    private var expiry: Job? = null

    /** Starts a spare if none is waiting. Cheap to call often; does nothing while one waits. */
    fun warm(context: Context) {
        val app = context.applicationContext
        HazelApp.instance.applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { start(app) }.onFailure { Log.w(TAG, "No spare gallery-dl: ${it.javaClass.simpleName}") }
        }
    }

    /**
     * The waiting spare for a run with [key] (the import path it was started with), handed
     * [args], or null when there is none or it no longer fits; the caller then starts cold.
     */
    fun take(key: String, args: List<String>): Pair<Process, File>? {
        val taken = synchronized(this) {
            val s = spare ?: return null
            spare = null
            expiry?.cancel()
            s
        }
        if (taken.key != key || !com.hazel.android.util.Processes.isAlive(taken.process)) {
            stop(taken)
            return null
        }
        return try {
            taken.process.outputStream.bufferedWriter().apply {
                write(JSONArray(args).toString())
                newLine()
                close()
            }
            taken.process to taken.stderr
        } catch (e: Exception) {
            stop(taken)
            null
        }
    }

    /** Ends a waiting spare, as an update swap needs before it replaces the files it loaded. */
    fun discard() {
        val s = synchronized(this) { spare.also { spare = null; expiry?.cancel() } } ?: return
        stop(s)
    }

    private fun start(context: Context) {
        val key = GalleryDlEngine.importKey(context)
        synchronized(this) {
            val waiting = spare
            if (waiting != null && waiting.key == key && com.hazel.android.util.Processes.isAlive(waiting.process)) return
        }
        discard()
        val stderr = GalleryLogs.rawFile(GalleryLogs.dir(context))
        val process = try {
            GalleryDlEngine.spareProcess(context, BOOT, stderr)
        } catch (e: Exception) {
            stderr.delete()
            throw e
        }
        val started = Spare(process, stderr, key)
        val replaced = synchronized(this) {
            spare.also {
                spare = started
                expiry?.cancel()
                expiry = HazelApp.instance.applicationScope.launch {
                    delay(IDLE_MS)
                    if (synchronized(this@GallerySpare) { spare === started }) discard()
                }
            }
        }
        replaced?.let(::stop)
    }

    private fun stop(s: Spare) {
        com.hazel.android.util.Processes.kill(s.process)
        s.stderr.delete()
    }

    /**
     * Loads gallery-dl, then waits for one line of JSON arguments and runs as `python -m gallery_dl`
     * would. The line is read from the byte stream: gallery-dl reconfigures stdin, which fails once
     * its text layer has been read.
     */
    private const val BOOT = GalleryLinkOutput.RUN_PREFIX + "import json\n" +
        "args = json.loads(sys.stdin.buffer.readline() or b'null')\n" +
        "if args is None: sys.exit(0)\n" +
        "sys.argv = ['gallery-dl'] + args\n" +
        "sys.exit(gallery_dl.main())"

    /** How long a spare waits unused before it is ended, so an idle app holds no extra process. */
    private const val IDLE_MS = 60_000L

    private const val TAG = "GallerySpare"
}
