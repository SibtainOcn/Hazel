package com.hazel.android.ui.screens.download

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hazel.android.BuildConfig
import com.hazel.android.R
import com.hazel.android.ui.motion.M3Motion
import com.hazel.android.ui.theme.LocalGlass
import com.hazel.android.ui.theme.glassBackdrop

enum class WhatsNewCard { APPEARANCE }

/**
 * What's new, shown once to people who update into a release that has something to tell.
 *
 * To announce a release, give it a new [RELEASE_ID] and list its [cards]. A release with
 * nothing to tell keeps the previous id (nobody sees anything) or empties [cards]. Fresh
 * installs never see it: the getting-started guide records the id as seen when it closes.
 */
object WhatsNew {
    const val RELEASE_ID = "1.1.15"
    val cards: List<WhatsNewCard> = listOf(WhatsNewCard.APPEARANCE)

    /** Null while either stored value is still loading, so nothing is decided early. */
    fun shouldShow(guideSeen: Boolean?, seenId: String?): Boolean? {
        if (guideSeen == null || seenId == null) return null
        return guideSeen && cards.isNotEmpty() && seenId != RELEASE_ID
    }
}

@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val cards = WhatsNew.cards
    if (cards.isEmpty()) return
    val total = cards.size
    var current by rememberSaveable { mutableIntStateOf(1) }
    val glass = LocalGlass.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    val slidePx = with(LocalDensity.current) { StepSlide.roundToPx() }
    val appearance = rememberAppearanceState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .glassBackdrop(shape),
            shape = shape,
            color = colors.surfaceContainerHigh,
            shadowElevation = if (glass) 0.dp else 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(shape = CircleShape, color = colors.primary) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = colors.onPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.whats_new_tag),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = colors.onPrimary
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.whats_new_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = current,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(M3Motion.emphasized(StepMillis)) { dir * slidePx } +
                            fadeIn(tween(FadeInMillis, delayMillis = FadeOutMillis, easing = LinearOutSlowInEasing))) togetherWith
                            (slideOutHorizontally(M3Motion.emphasized(StepMillis)) { -dir * slidePx } +
                                fadeOut(tween(FadeOutMillis, easing = FastOutLinearInEasing))) using
                            SizeTransform(clip = false) { _, _ -> snap() }
                    },
                    label = "whatsNewCard"
                ) { index ->
                    when (cards[(index - 1).coerceIn(0, total - 1)]) {
                        WhatsNewCard.APPEARANCE -> AppearanceStep(
                            state = appearance,
                            title = stringResource(R.string.whats_new_glass_title),
                            description = stringResource(R.string.whats_new_glass_desc)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (total > 1) {
                    StepDots(total = total, current = current)
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // layout-safe: an optional icon button beside one action that takes the rest of the row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (total > 1) {
                        FilledTonalIconButton(
                            onClick = { if (current > 1) current-- },
                            enabled = current > 1,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colors.surfaceContainerHighest,
                                contentColor = colors.onSurface
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.back),
                                contentDescription = stringResource(R.string.guide_back),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    val last = current >= total
                    Button(
                        onClick = { if (last) onDismiss() else current++ },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentPadding = ActionPadding
                    ) {
                        Text(
                            text = stringResource(if (last) R.string.whats_new_done else R.string.guide_next),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
