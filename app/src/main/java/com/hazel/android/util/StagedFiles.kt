package com.hazel.android.util

import java.io.File

/** A finished file in a work folder, and the folders it sits in beneath it, as `a/b`. */
data class StagedFile(val file: File, val subPath: String)

/**
 * Reads a download's work folder, including subfolders from the user's output template.
 * Plain `java.io` so it is testable without Android. Symbolic links are never followed;
 * skipped ones are reported through `onSkipped`, and the depth is capped.
 */
object StagedFiles {

    /** Folders deep below the work folder that are looked into. */
    const val MAX_DEPTH = 6

    private val REJECTED_IN_NAME = Regex("""[\\/:*?"<>|\u0000-\u001F\u007F]""")

    /**
     * [subPath] (`a/b`) as published: FAT-forbidden characters become `_`, leading spaces and trailing dots/spaces go,
     * a leading dot becomes `_`, an empty name is `_`. The same folder is then found next time.
     */
    fun publishSubPath(subPath: String): String =
        if (subPath.isEmpty()) "" else subPath.split('/').joinToString("/") { publishName(it) }

    internal fun publishName(name: String): String {
        var clean = REJECTED_IN_NAME.replace(name, "_").trimStart(' ').trimEnd('.', ' ')
        if (clean.startsWith(".")) clean = "_" + clean.trimStart('.')
        return clean.ifEmpty { "_" }
    }

    /**
     * Every file under [root] with its folders, ordered by path. Empty when [root] is missing or unreadable.
     * [checkLinks] is for anything that moves or deletes files; a size readout can turn it off.
     */
    fun list(
        root: File,
        maxDepth: Int = MAX_DEPTH,
        checkLinks: Boolean = true,
        onSkipped: (File) -> Unit = {}
    ): List<StagedFile> {
        val out = ArrayList<StagedFile>()
        val canonical = if (checkLinks) canonicalOrNull(root) ?: run { onSkipped(root); return out } else null
        walk(root, canonical, emptyList(), maxDepth, checkLinks, onSkipped, out)
        return out
    }

    /** Deletes empty folders under [root], deepest first, keeping [root] itself. */
    fun pruneEmptyFolders(root: File, maxDepth: Int = MAX_DEPTH) {
        if (maxDepth < 0) return
        val children = try {
            root.listFiles()
        } catch (_: SecurityException) {
            null
        } ?: return
        for (child in children) {
            if (child.isDirectory && !isLink(child)) {
                pruneEmptyFolders(child, maxDepth - 1)
                runCatching { child.delete() }
            }
        }
    }

    /** True when [file] is a symbolic link. Fails closed: an unreadable path counts as one. */
    fun isLink(file: File): Boolean {
        val parent = file.absoluteFile.parentFile ?: return false
        val canonicalParent = canonicalOrNull(parent) ?: return true
        return isLinkIn(file, canonicalParent)
    }

    /** [file] against its folder's canonical path, which the caller has worked out once. */
    private fun isLinkIn(file: File, canonicalParent: File): Boolean = try {
        file.canonicalPath != File(canonicalParent, file.name).path
    } catch (_: Exception) {
        true
    }

    private fun canonicalOrNull(dir: File): File? = try {
        dir.canonicalFile
    } catch (_: Exception) {
        null
    }

    private fun walk(
        dir: File,
        canonicalDir: File?,
        parts: List<String>,
        depthLeft: Int,
        checkLinks: Boolean,
        onSkipped: (File) -> Unit,
        out: MutableList<StagedFile>
    ) {
        val children = try {
            dir.listFiles()
        } catch (_: SecurityException) {
            null
        } ?: return
        for (child in children.sortedBy { it.name }) {
            if (checkLinks && canonicalDir != null && isLinkIn(child, canonicalDir)) {
                onSkipped(child)
                continue
            }
            when {
                child.isFile -> out += StagedFile(child, parts.joinToString("/"))
                child.isDirectory && depthLeft > 0 -> {
                    // One path lookup per folder, not per file in it.
                    val canonicalChild = if (checkLinks) canonicalOrNull(child) else null
                    if (checkLinks && canonicalChild == null) {
                        onSkipped(child)
                    } else {
                        walk(child, canonicalChild, parts + child.name, depthLeft - 1, checkLinks, onSkipped, out)
                    }
                }
            }
        }
    }
}
