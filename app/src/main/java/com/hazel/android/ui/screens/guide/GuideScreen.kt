package com.hazel.android.ui.screens.guide

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazel.android.R

@Composable
fun GuideScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // Compact header with custom back arrow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.back),
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Quick Guide",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // ── App preview screenshot 1 ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Image(
                painter = painterResource(R.drawable.p1),
                contentDescription = "Hazel supported platforms",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.FillWidth
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── Share to Download (top priority) ──
            GuideSection(
                title = "Share to Download",
                steps = listOf(
                    "Open any supported app (YouTube, Instagram, etc.)",
                    "Tap Share on any video or post",
                    "Select Hazel from the share menu",
                    "Download starts automatically with your default settings"
                )
            )

            // ── Single Download ──
            GuideSection(
                title = "Single Download",
                steps = listOf(
                    "Paste any URL from YouTube, Instagram, Spotify, Twitter or 3000+ platforms",
                    "Choose Video or Audio format",
                    "Select quality · up to 4K for video, 320kbps for audio",
                    "Tap Download · file saved to Download/Hazel/"
                )
            )

            // ── App preview screenshot 2 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Image(
                    painter = painterResource(R.drawable.p2),
                    contentDescription = "Hazel app interface",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.FillWidth
                )
            }

            // ── Playlist Mode ──
            GuideSection(
                title = "Playlist Mode",
                steps = listOf(
                    "Set Mode to Playlist",
                    "Paste the playlist URL",
                    "All videos/audios download sequentially",
                    "Each file saved with its original title"
                )
            )

            // ── Multi Links ──
            GuideSection(
                title = "Multi Links Mode",
                steps = listOf(
                    "Set Mode to Multi Links",
                    "Paste first URL → tap + Next URL",
                    "Add up to 10 URLs one by one",
                    "Tap Review to verify, then Download All"
                )
            )

            // ── Bulk Import ──
            GuideSection(
                title = "Bulk Import",
                steps = listOf(
                    "Set Mode to Bulk",
                    "Tap Download to open the Bulk Editor",
                    "Paste URLs line by line, or import from a TXT/CSV file",
                    "Review and start batch download"
                )
            )

            // ── Video to Audio Converter ──
            GuideSection(
                title = "Video → Audio Converter",
                steps = listOf(
                    "Go to the Converter tab",
                    "Pick any video file from your device",
                    "Choose output format · MP3, AAC, FLAC, WAV, or Opus",
                    "Tap Convert · saved to Music/Hazel/"
                )
            )

            // ── Settings ──
            GuideSection(
                title = "Settings",
                steps = listOf(
                    "Download Location · shows where files are saved",
                    "Accent Color · personalize the app's theme color",
                    "Dark/Light mode · toggle from the top-right icon"
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GuideSection(title: String, steps: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(18.dp)
                    )
                    Text(
                        step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
