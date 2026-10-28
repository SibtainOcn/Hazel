package com.hazel.android.download.extractor.newpipe

import com.hazel.android.download.extractor.ListingSource
import com.hazel.android.download.playback.AudioTrack
import com.hazel.android.download.playback.AudioTracks
import com.hazel.android.download.playback.AudioWish
import com.hazel.android.download.playback.PlayableStream
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.MediaFormat as NewPipeFormat

/** Picks a stream to play from what NewPipe reads for a link. */
internal object NewPipeStreams {

    fun playable(url: String, maxHeight: Int, wish: AudioWish = AudioWish()): PlayableStream? {
        val service = NewPipeLister.service(url) ?: return null
        val info = StreamInfo.getInfo(service, url)

        val live = info.streamType == StreamType.LIVE_STREAM ||
            info.streamType == StreamType.AUDIO_LIVE_STREAM
        val hls = info.hlsUrl?.takeIf { it.isNotBlank() }
        val dash = info.dashMpdUrl?.takeIf { it.isNotBlank() }
        if (live && hls != null) return hlsStream(hls, maxHeight)
        if (live && dash != null) return dashStream(dash, maxHeight)

        val muxed = withinCap(info.videoStreams.orEmpty(), maxHeight)
        val videoOnly = withinCap(info.videoOnlyStreams.orEmpty(), maxHeight)
        val sounds = info.audioStreams.orEmpty().filter { it.isPlainHttp() }
        // One best stream per track; more than one means the listener can choose.
        val byTrack = sounds.groupBy { it.trackKey() }.mapNotNull { (_, group) -> group.maxWithOrNull(audioOrder) }
        val tracks = if (byTrack.size > 1) byTrack.map { it.toAudioTrack() } else emptyList()
        val track = AudioTracks.choose(tracks, wish)
        val audio = track?.let { t -> byTrack.firstOrNull { it.content == t.url } }
            ?: sounds.maxWithOrNull(audioOrder)

        val heights = (info.videoStreams.orEmpty() + info.videoOnlyStreams.orEmpty())
            .filter { it.isPlainHttp() && it.quality() > 0 }
            .map { it.quality() }.distinct().sortedDescending()

        val chosen = when {
            // With several tracks the sound is kept apart, so it can be swapped alone.
            videoOnly != null && audio != null &&
                (muxed == null || videoOnly.quality() > muxed.quality() || tracks.isNotEmpty()) ->
                PlayableStream(
                    url = videoOnly.content,
                    audioUrl = audio.content,
                    headers = headersFor(videoOnly.content),
                    audioHeaders = headersFor(audio.content),
                    engine = ListingSource.NEWPIPE
                )
            muxed != null -> PlayableStream(
                url = muxed.content,
                headers = headersFor(muxed.content),
                engine = ListingSource.NEWPIPE
            )
            dash != null -> dashStream(dash, maxHeight)
            audio != null -> PlayableStream(
                url = audio.content,
                hasVideo = false,
                headers = headersFor(audio.content),
                engine = ListingSource.NEWPIPE
            )
            hls != null -> hlsStream(hls, maxHeight)
            else -> null
        }
        val playingTrack = track?.takeIf { chosen != null && (chosen.audioUrl ?: chosen.url) == it.url }
        return chosen?.copy(
            heights = heights,
            audioTracks = if (playingTrack != null) tracks else emptyList(),
            audioTrackId = playingTrack?.id
        )
    }

    private fun AudioStream.trackKey(): String? =
        audioTrackId?.takeIf { it.isNotBlank() } ?: audioLocale?.toLanguageTag()?.takeIf { it != "und" }

    private fun AudioStream.toAudioTrack() = AudioTrack(
        id = trackKey() ?: content,
        language = audioLocale?.toLanguageTag()?.takeIf { it != "und" }
            ?: audioTrackId?.substringBefore('.')?.takeIf { it.isNotBlank() },
        original = audioTrackType == AudioTrackType.ORIGINAL,
        descriptive = audioTrackType == AudioTrackType.DESCRIPTIVE,
        url = content,
        headers = headersFor(content)
    )

    private fun hlsStream(address: String, maxHeight: Int) = PlayableStream(
        url = address,
        isHls = true,
        adaptiveCap = maxHeight,
        headers = headersFor(address),
        engine = ListingSource.NEWPIPE
    )

    /** The tallest stream within [maxHeight], or the smallest there is when none fits. */
    private fun withinCap(streams: List<VideoStream>, maxHeight: Int): VideoStream? {
        val playable = streams.filter { it.isPlainHttp() && it.quality() > 0 }
        return playable.filter { it.quality() <= maxHeight }.maxWithOrNull(videoOrder)
            ?: playable.minByOrNull { it.quality() }
    }

    private fun dashStream(address: String, maxHeight: Int) = PlayableStream(
        url = address,
        isDash = true,
        adaptiveCap = maxHeight,
        headers = headersFor(address),
        engine = ListingSource.NEWPIPE
    )

    private fun Stream.isPlainHttp() =
        deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && isUrl && !content.isNullOrBlank()

    /** Taller first, then MP4, which every device decodes. */
    private val videoOrder = compareBy<VideoStream>({ it.quality() }, { it.format == NewPipeFormat.MPEG_4 })

    /** Lines on the shorter side, so a tall short counts as the quality it is labelled. */
    private fun VideoStream.quality(): Int =
        if (height > 0 && width > 0) minOf(height, width) else height

    /** The original track over dubbed ones, then M4A, then the higher bitrate. */
    private val audioOrder = compareBy<AudioStream>(
        { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL },
        { it.format == NewPipeFormat.M4A },
        { it.averageBitrate }
    )

    /** YouTube serves a stream only to the kind of client it was issued for. */
    private fun headersFor(streamUrl: String): Map<String, String> {
        val localization = NewPipe.getPreferredLocalization()
        val agent = when {
            YoutubeParsingHelper.isAndroidStreamingUrl(streamUrl) ->
                YoutubeParsingHelper.getAndroidUserAgent(localization)
            YoutubeParsingHelper.isIosStreamingUrl(streamUrl) ->
                YoutubeParsingHelper.getIosUserAgent(localization)
            YoutubeParsingHelper.isVisionOsStreamingUrl(streamUrl) ->
                YoutubeParsingHelper.getVisionOsUserAgent(localization)
            else -> NewPipeDownloader.USER_AGENT
        }
        val headers = mutableMapOf("User-Agent" to agent)
        if (YoutubeParsingHelper.isWebStreamingUrl(streamUrl)) {
            headers["Origin"] = "https://www.youtube.com"
            headers["Referer"] = "https://www.youtube.com/"
        }
        return headers
    }
}
