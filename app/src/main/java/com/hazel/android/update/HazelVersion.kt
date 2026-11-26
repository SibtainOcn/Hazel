package com.hazel.android.update

/**
 * A Hazel version as releases name it: 1.1.18, 1.1.18-beta.2, 1.1.18-nightly.14.
 *
 * Ordered the way the version codes are: for the same numbers a nightly comes before a beta
 * and a beta before the stable, and within a stage the build number decides.
 */
data class HazelVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val stage: Stage,
    val build: Int
) : Comparable<HazelVersion> {

    /** Least to most settled, so a higher stage is the newer build of the same numbers. */
    enum class Stage(val word: String) { NIGHTLY("nightly"), BETA("beta"), STABLE("stable") }

    override fun compareTo(other: HazelVersion): Int = compareValuesBy(
        this, other, { it.major }, { it.minor }, { it.patch }, { it.stage }, { it.build }
    )

    override fun toString(): String =
        if (stage == Stage.STABLE) "$major.$minor.$patch" else "$major.$minor.$patch-${stage.word}.$build"

    companion object {
        private val PATTERN = Regex(
            """v?(\d+)\.(\d+)\.(\d+)(?:-(nightly|beta|rc|alpha)\.?(\d+)?)?""",
            RegexOption.IGNORE_CASE
        )

        /** The whole text is a version, or null. */
        fun parse(text: String?): HazelVersion? =
            text?.trim()?.let { PATTERN.matchEntire(it) }?.let(::from)

        /** The first version written anywhere in the text, such as a release title or an APK name. */
        fun find(text: String?): HazelVersion? =
            text?.let { PATTERN.find(it) }?.let(::from)

        private fun from(match: MatchResult): HazelVersion? {
            val (major, minor, patch, suffix, number) = match.destructured
            val stage = when {
                suffix.isEmpty() -> Stage.STABLE
                suffix.equals("nightly", ignoreCase = true) -> Stage.NIGHTLY
                // rc and any other pre-release word ship through the beta channel.
                else -> Stage.BETA
            }
            return HazelVersion(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                patch.toIntOrNull() ?: return null,
                stage,
                number.toIntOrNull() ?: 0
            )
        }
    }
}
