package com.hazel.android.ui.screens.download

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hazel.android.R
import com.hazel.android.util.PermissionHelper

/**
 * Getting-started onboarding stepper carousel, shown on first app launch.
 *
 * 5 slides introduce core capabilities:
 * 1. Paste and Download
 * 2. Format Selection
 * 3. Hazel Instant
 * 4. Battery Optimization (Deny or Allow options of equal size)
 * 5. Notifications (Deny or Allow options of equal size)
 *
 * Designed with a deep dark (#000000 / #0A0A0A) background, fine subtle blue
 * gradient, crisp white primary actions, and clean minimalist styling.
 */
@Composable
fun GettingStartedDialog(
    onOpenBatterySettings: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 5
    val context = LocalContext.current

    // Dismiss with notification permission request
    val dismissWithPermission: () -> Unit = {
        onDismiss()
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionHelper.ensureNotificationPermission(context)
        }
    }

    // Dismiss without requesting notification permission now
    val dismissNoPermission: () -> Unit = {
        onDismiss()
    }

    Dialog(
        onDismissRequest = dismissWithPermission,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color.Transparent,
            shadowElevation = 24.dp
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0D111A), // Fine subtle dark blue tone at top
                                Color(0xFF07090E), // Deep dark surface
                                Color(0xFF000000)  // Pitch black
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(32.dp)
                    )
            ) {
                // Subtle ambient glow in top-right
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .offset(x = 40.dp, y = (-48).dp)
                        .align(Alignment.TopEnd)
                        .blur(36.dp)
                        .background(
                            Color(0xFF1E293B).copy(alpha = 0.12f),
                            CircleShape
                        )
                )

                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header: Flat step badge on left, clean Skip text on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flat badge without border
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF161616)
                        ) {
                            Text(
                                text = stringResource(R.string.guide_step_badge, currentStep, totalSteps),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        // Clean Skip button
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = dismissWithPermission)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.guide_skip),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8E8E93)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Slide Content
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            (slideInHorizontally(
                                initialOffsetX = { if (targetState > initialState) it / 4 else -it / 4 },
                                animationSpec = tween(300)
                            ) + fadeIn(tween(300))) togetherWith
                                    (slideOutHorizontally(
                                        targetOffsetX = { if (targetState > initialState) -it / 4 else it / 4 },
                                        animationSpec = tween(300)
                                    ) + fadeOut(tween(200)))
                        }
                    ) { step ->
                        val data = stepData(step)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Icon container with subtle border
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = RoundedCornerShape(26.dp),
                                color = Color(0xFF141414),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Color.White.copy(alpha = 0.08f)
                                )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(data.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Step title
                            Text(
                                text = stringResource(data.titleRes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                letterSpacing = (-0.5).sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Step description
                            Text(
                                text = stringResource(data.descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA0A0A0),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .height(50.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Progress Dots
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (i in 1..totalSteps) {
                            val width by animateDpAsState(
                                targetValue = if (i == currentStep) 28.dp else 8.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .width(width)
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == currentStep) Color.White
                                        else Color(0xFF262626)
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back button (enabled on steps 2..5)
                        Surface(
                            onClick = { if (currentStep > 1) currentStep-- },
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = Color(0xFF141414),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                Color.White.copy(alpha = 0.08f)
                            ),
                            enabled = currentStep > 1
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.back),
                                    contentDescription = stringResource(R.string.guide_back),
                                    modifier = Modifier.size(20.dp),
                                    tint = if (currentStep > 1) Color.White
                                    else Color(0xFF404040)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        if (currentStep == 4) {
                            // Step 4 (Battery Optimization): Deny & Allow buttons of equal size
                            Surface(
                                onClick = { currentStep++ },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = CircleShape,
                                color = Color(0xFF161616),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Color.White.copy(alpha = 0.12f)
                                )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.guide_deny),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                onClick = {
                                    onOpenBatterySettings()
                                    currentStep++
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 4.dp
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.guide_allow),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        } else if (currentStep == 5) {
                            // Step 5 (Notifications): Deny & Allow buttons of equal size
                            Surface(
                                onClick = dismissNoPermission,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = CircleShape,
                                color = Color(0xFF161616),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Color.White.copy(alpha = 0.12f)
                                )
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.guide_deny),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                onClick = dismissWithPermission,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 4.dp
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.guide_allow),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        } else {
                            // Steps 1..3: Primary Next button
                            Surface(
                                onClick = { currentStep++ },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 4.dp
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.guide_next),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        painter = painterResource(R.drawable.small_chevron),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Step data definitions

private data class StepInfo(
    val titleRes: Int,
    val descRes: Int,
    val iconRes: Int
)

/**
 * Returns the content for each of the 5 getting-started steps.
 *
 * Step 1: Paste and Download (link icon)
 * Step 2: Format Selection (sliders-horizontal icon)
 * Step 3: Hazel Instant (the app's own Hazel SVG bolt icon)
 * Step 4: Battery Optimization (battery charge icon)
 * Step 5: Notifications (bell icon)
 */
private fun stepData(step: Int): StepInfo = when (step) {
    1 -> StepInfo(
        titleRes = R.string.guide_step1_title,
        descRes = R.string.guide_step_paste_link,
        iconRes = R.drawable.ic_link
    )
    2 -> StepInfo(
        titleRes = R.string.guide_step2_title,
        descRes = R.string.guide_step_pick_format,
        iconRes = R.drawable.more_tab
    )
    3 -> StepInfo(
        titleRes = R.string.guide_step3_title,
        descRes = R.string.guide_step_share_instant,
        iconRes = R.drawable.ic_hazel_bolt
    )
    4 -> StepInfo(
        titleRes = R.string.guide_step4_title,
        descRes = R.string.guide_step_battery_unrestricted,
        iconRes = R.drawable.battery_charge
    )
    5 -> StepInfo(
        titleRes = R.string.guide_step5_title,
        descRes = R.string.guide_step_notifications,
        iconRes = R.drawable.ic_bell
    )
    else -> stepData(1)
}

/**
 * Whether Android is already leaving this app alone in the background.
 *
 * A download is a long running network job, and outside this exemption the system stops it
 * shortly after the app loses the foreground, which the user experiences as downloads that
 * never finish.
 */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    return runCatching {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
}

/**
 * Direct request to ignore battery optimizations, triggering the system grant dialog
 * directly on top of the app.
 *
 * Tested across min to max SDK. On devices that reject the direct prompt, falls back
 * gracefully to battery optimization settings and app details.
 */
fun openBatterySettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = android.net.Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    for (intent in listOf(direct, fallback, appDetails)) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}
