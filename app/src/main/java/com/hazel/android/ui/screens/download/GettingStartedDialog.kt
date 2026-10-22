package com.hazel.android.ui.screens.download

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hazel.android.R
import com.hazel.android.data.SettingsRepository
import com.hazel.android.ui.components.AccentPickerDialog
import com.hazel.android.ui.components.ActionsRow
import com.hazel.android.ui.components.EqualWidthActions
import com.hazel.android.ui.components.GlassStylePickerDialog
import com.hazel.android.ui.components.GlassStyleSwatch
import com.hazel.android.ui.motion.M3Motion
import com.hazel.android.ui.theme.AccentColors
import com.hazel.android.ui.theme.GlassStyle
import com.hazel.android.ui.theme.LocalDarkTheme
import com.hazel.android.ui.theme.LocalGlass
import com.hazel.android.ui.theme.glassBackdrop
import com.hazel.android.util.PermissionHelper
import kotlinx.coroutines.launch

/**
 * Getting-started onboarding stepper carousel, shown on first app launch.
 *
 * 7 slides introduce core capabilities:
 * 1. Paste and Download
 * 2. Appearance (theme, accent, glass), saved as soon as it is picked
 * 3. Format Selection
 * 4. Share to Hazel
 * 5. Hazel Instant
 * 6. Battery Optimization (Deny or Allow options of equal size)
 * 7. Notifications (Deny or Allow options of equal size)
 *
 * Follows the app theme: accent coloured Material 3 actions on the glass surface when glass is on.
 */
@Composable
fun GettingStartedDialog(
    onOpenBatterySettings: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 7
    val context = LocalContext.current
    val glass = LocalGlass.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    val slidePx = with(LocalDensity.current) { StepSlide.roundToPx() }
    val appearance = rememberAppearanceState()

    val dismissWithPermission: () -> Unit = {
        onDismiss()
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionHelper.ensureNotificationPermission(context)
        }
    }
    val next: () -> Unit = { if (currentStep < totalSteps) currentStep++ }

    Dialog(
        onDismissRequest = dismissWithPermission,
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
                ActionsRow(
                    modifier = Modifier.fillMaxWidth(),
                    start = {
                        Surface(shape = CircleShape, color = colors.primary.copy(alpha = 0.14f)) {
                            Text(
                                text = stringResource(R.string.guide_step_badge, currentStep, totalSteps),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = colors.primary
                            )
                        }
                    }
                ) {
                    TextButton(
                        onClick = dismissWithPermission,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant)
                    ) {
                        Text(stringResource(R.string.guide_skip), fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = currentStep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .steadyHeight(StepMinHeight),
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(M3Motion.emphasized(StepMillis)) { dir * slidePx } +
                            fadeIn(tween(FadeInMillis, delayMillis = FadeOutMillis, easing = LinearOutSlowInEasing))) togetherWith
                            (slideOutHorizontally(M3Motion.emphasized(StepMillis)) { -dir * slidePx } +
                                fadeOut(tween(FadeOutMillis, easing = FastOutLinearInEasing))) using
                            SizeTransform(clip = false) { _, _ -> snap() }
                    },
                    label = "guideStep"
                ) { step ->
                    if (step == APPEARANCE_STEP) {
                        AppearanceStep(appearance)
                    } else {
                        val data = stepData(step)
                        StepHeader(
                            icon = painterResource(data.iconRes),
                            title = stringResource(data.titleRes),
                            description = stringResource(data.descRes)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in 1..totalSteps) {
                        val active = i == currentStep
                        val width by animateDpAsState(
                            targetValue = if (active) 28.dp else 8.dp,
                            animationSpec = M3Motion.emphasized(StepMillis),
                            label = "dotWidth"
                        )
                        val dot by animateColorAsState(
                            targetValue = if (active) colors.primary else colors.onSurface.copy(alpha = 0.16f),
                            animationSpec = tween(StepMillis),
                            label = "dotColor"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .width(width)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(dot)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // layout-safe: an icon button beside one action that takes the rest of the row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { if (currentStep > 1) currentStep-- },
                        enabled = currentStep > 1,
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

                    when (currentStep) {
                        BATTERY_STEP -> DenyAllow(
                            modifier = Modifier.weight(1f),
                            onDeny = next,
                            onAllow = {
                                onOpenBatterySettings()
                                next()
                            }
                        )
                        totalSteps -> DenyAllow(
                            modifier = Modifier.weight(1f),
                            onDeny = onDismiss,
                            onAllow = dismissWithPermission
                        )
                        else -> Button(
                            onClick = next,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            contentPadding = ActionPadding
                        ) {
                            Text(
                                text = stringResource(R.string.guide_next),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(R.drawable.small_chevron),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DenyAllow(modifier: Modifier, onDeny: () -> Unit, onAllow: () -> Unit) {
    EqualWidthActions(modifier = modifier, spacing = 10.dp) {
        OutlinedButton(
            onClick = onDeny,
            modifier = Modifier.height(48.dp),
            contentPadding = ActionPadding
        ) {
            Text(
                text = stringResource(R.string.guide_deny),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Button(
            onClick = onAllow,
            modifier = Modifier.height(48.dp),
            contentPadding = ActionPadding
        ) {
            Text(
                text = stringResource(R.string.guide_allow),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StepHeader(icon: Painter, title: String, description: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.primary.copy(alpha = 0.14f))
        ) {
            Icon(painter = icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = colors.primary)
        }
        Spacer(modifier = Modifier.height(20.dp))
        StepTitle(title)
        Spacer(modifier = Modifier.height(8.dp))
        StepDescription(description, Modifier.heightIn(min = 50.dp))
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        letterSpacing = (-0.5).sp
    )
}

@Composable
private fun StepDescription(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 18.sp,
        modifier = modifier.padding(horizontal = 8.dp)
    )
}

private const val StepMillis = 300
private const val FadeOutMillis = 90
private const val FadeInMillis = 210
private val StepSlide = 30.dp
private val StepMinHeight = 292.dp
private val ActionPadding = PaddingValues(horizontal = 16.dp)

@Composable
private fun Modifier.steadyHeight(min: Dp): Modifier {
    val tallest = remember { IntArray(1) }
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = maxOf(placeable.height, min.roundToPx(), tallest[0])
        tallest[0] = height
        layout(placeable.width, height) { placeable.place(0, (height - placeable.height) / 2) }
    }
}

/**
 * Appearance choices for the guide. Read once when the guide opens, and switches flip
 * at once on tap while the setting is saved in the background.
 */
private class AppearanceState(
    dark: Boolean,
    glass: Boolean,
    val accent: () -> String,
    val styleKey: () -> String,
    val setDark: (Boolean) -> Unit,
    val setGlass: (Boolean) -> Unit,
    val setAccent: (String) -> Unit,
    val setStyle: (GlassStyle) -> Unit,
) {
    var dark by mutableStateOf(dark)
    var glass by mutableStateOf(glass)
}

@Composable
private fun rememberAppearanceState(): AppearanceState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = LocalDarkTheme.current
    val glass = LocalGlass.current
    val accent by remember(context) { SettingsRepository.getAccentColor(context) }.collectAsState(initial = null)
    val styleKey by remember(context) { SettingsRepository.getGlassStyle(context) }.collectAsState(initial = null)
    val save: (suspend () -> Unit) -> Unit = { write -> scope.launch { runCatching { write() } } }
    return remember(context) {
        AppearanceState(
            dark = dark,
            glass = glass,
            accent = { accent ?: AccentColors.first().name },
            styleKey = { styleKey ?: GlassStyle.AURORA.key },
            setDark = { on -> save { SettingsRepository.setDarkTheme(context, on) } },
            setGlass = { on -> save { SettingsRepository.setGlassEnabled(context, on) } },
            setAccent = { name -> save { SettingsRepository.setAccentColor(context, name) } },
            setStyle = { style -> save { SettingsRepository.setGlassStyle(context, style.key) } },
        )
    }
}

@Composable
private fun AppearanceStep(state: AppearanceState) {
    val colors = MaterialTheme.colorScheme
    val accent = state.accent()
    val style = GlassStyle.fromKey(state.styleKey())
    val accentColor = (AccentColors.find { it.name == accent } ?: AccentColors.first()).let {
        if (state.dark) it.dark else it.light
    }
    var accentPicker by remember { mutableStateOf(false) }
    var stylePicker by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        StepTitle(stringResource(R.string.guide_step_appearance_title))
        Spacer(modifier = Modifier.height(6.dp))
        StepDescription(stringResource(R.string.guide_step_appearance_desc))
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainerHighest)
        ) {
            ToggleRow(stringResource(R.string.appearance_dark_theme), state.dark) { on ->
                state.dark = on
                state.setDark(on)
            }
            RowDivider()
            PickRow(stringResource(R.string.appearance_accent_color), enabled = true, onClick = { accentPicker = true }) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(accentColor))
            }
            RowDivider()
            ToggleRow(stringResource(R.string.appearance_glass), state.glass) { on ->
                state.glass = on
                state.setGlass(on)
            }
            RowDivider()
            PickRow(stringResource(R.string.glass_style_title), enabled = state.glass, onClick = { stylePicker = true }) {
                GlassStyleSwatch(style, accent)
            }
        }
    }

    if (accentPicker) {
        AccentPickerDialog(
            accentName = accent,
            onAccentChanged = state.setAccent,
            onDismiss = { accentPicker = false }
        )
    }
    if (stylePicker) {
        GlassStylePickerDialog(
            current = style,
            accentName = accent,
            onPick = state.setStyle,
            onDismiss = { stylePicker = false }
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowLabel(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun PickRow(label: String, enabled: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowLabel(label, Modifier.weight(1f))
        trailing()
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            painter = painterResource(R.drawable.small_chevron),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RowLabel(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

private data class StepInfo(
    val titleRes: Int,
    val descRes: Int,
    val iconRes: Int
)

private const val APPEARANCE_STEP = 2
private const val BATTERY_STEP = 6

private fun stepData(step: Int): StepInfo = when (step) {
    1 -> StepInfo(
        titleRes = R.string.guide_step1_title,
        descRes = R.string.guide_step_paste_link,
        iconRes = R.drawable.ic_link
    )
    3 -> StepInfo(
        titleRes = R.string.guide_step2_title,
        descRes = R.string.guide_step_pick_format,
        iconRes = R.drawable.more_tab
    )
    4 -> StepInfo(
        titleRes = R.string.guide_step_share_title,
        descRes = R.string.guide_step_share_sheet,
        iconRes = R.drawable.ic_share
    )
    5 -> StepInfo(
        titleRes = R.string.guide_step3_title,
        descRes = R.string.guide_step_share_instant,
        iconRes = R.drawable.ic_hazel_bolt
    )
    6 -> StepInfo(
        titleRes = R.string.guide_step4_title,
        descRes = R.string.guide_step_battery_unrestricted,
        iconRes = R.drawable.battery_charge
    )
    7 -> StepInfo(
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
