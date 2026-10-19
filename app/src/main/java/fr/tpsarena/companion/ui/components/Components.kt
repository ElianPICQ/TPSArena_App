package fr.tpsarena.companion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.tpsarena.companion.data.Format
import fr.tpsarena.companion.data.Labels
import fr.tpsarena.companion.data.Presence
import fr.tpsarena.companion.ui.theme.ButtonShape
import fr.tpsarena.companion.ui.theme.Neon
import fr.tpsarena.companion.ui.theme.PanelShape
import fr.tpsarena.companion.ui.theme.SmallButtonShape

/** Background of the PWA without WebGL (`.neon-bg__fallback`): halos, horizon line, grid, vignette. */
@Composable
fun NeonBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().background(Neon.bg)) {
        val w = size.width
        val h = size.height
        drawRect(Brush.radialGradient(listOf(Neon.magenta.copy(alpha = 0.16f), Color.Transparent), Offset(w * 0.86f, h * 0.12f), maxOf(w, h) * 0.6f))
        drawRect(Brush.radialGradient(listOf(Neon.cyan.copy(alpha = 0.12f), Color.Transparent), Offset(w * 0.08f, h * 0.95f), maxOf(w, h) * 0.6f))
        // Grid under the horizon (lower 42 %, a line every 80 px).
        val gridTop = h * 0.58f
        var x = 0f
        while (x < w) {
            drawLine(Neon.cyan.copy(alpha = 0.07f), Offset(x, gridTop), Offset(x, h), strokeWidth = 1f)
            x += 80.dp.toPx()
        }
        val glow = 6.dp.toPx()
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Neon.magenta.copy(alpha = 0.35f), Color.Transparent),
                startY = gridTop - glow,
                endY = gridTop + glow,
            ),
            topLeft = Offset(0f, gridTop - glow),
            size = androidx.compose.ui.geometry.Size(w, glow * 2),
        )
        drawRect(Brush.radialGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f), center = center, radius = maxOf(w, h) * 0.75f))
    }
}

/** Chamfered panel (`.panel`), optionally clickable. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    border: Color = Neon.panelBorder,
    light: Boolean = false,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(PanelShape)
            .background(if (light) Neon.panelLight else Neon.panel)
            .border(1.dp, border, PanelShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    val shape: Shape = if (small) SmallButtonShape else ButtonShape
    val border = if (primary) Neon.cyan.copy(alpha = 0.6f) else Neon.btnBorder
    Box(
        modifier
            .defaultMinSize(minHeight = if (small) 40.dp else 48.dp)
            .clip(shape)
            .background(Neon.btnBg)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 18.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = if (small) 16.sp else 19.sp),
            color = if (enabled) Neon.text else Neon.text.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun NeonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = Neon.muted) } },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Neon.fieldBg,
            unfocusedContainerColor = Neon.fieldBg,
            disabledContainerColor = Neon.fieldBg,
            focusedBorderColor = Neon.cyan,
            unfocusedBorderColor = Neon.btnBorder,
            focusedLabelColor = Neon.cyan,
            unfocusedLabelColor = Neon.muted,
            cursorColor = Neon.cyan,
        ),
    )
}

enum class NoteKind { INFO, WARN, ERROR }

/** Note with a colored left border (`.note`). */
@Composable
fun Note(text: String, modifier: Modifier = Modifier, kind: NoteKind = NoteKind.INFO) {
    val color = when (kind) {
        NoteKind.INFO -> Neon.cyan
        NoteKind.WARN -> Neon.yellow
        NoteKind.ERROR -> Neon.red
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
            .background(color.copy(alpha = if (kind == NoteKind.INFO) 0.06f else 0.08f)),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(color))
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.semantics { heading() }, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text.uppercase(), style = MaterialTheme.typography.headlineSmall)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.titleMedium, color = Neon.muted)
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = Neon.muted, textAlign = textAlign)
}

@Composable
fun Loading(label: String = "Chargement…", modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = Neon.cyan, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        Text(label, color = Neon.muted)
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Note(message, kind = NoteKind.ERROR)
        if (onRetry != null) NeonButton("Réessayer", onRetry)
    }
}

@Composable
fun Empty(text: String, modifier: Modifier = Modifier) {
    Muted(text, modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center)
}

/** Banner for saved data: « Mise à jour… » while loading, else « Hors ligne ». */
@Composable
fun CachedNote(cachedAt: Long?, loading: Boolean) {
    if (cachedAt == null) return
    Note(
        if (loading) "Mise à jour…" else "Hors ligne, données du ${Format.dateTime(cachedAt)}",
        kind = if (loading) NoteKind.INFO else NoteKind.WARN,
    )
}

@Composable
fun Badge(count: Int, modifier: Modifier = Modifier, description: String? = null) {
    Box(
        modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Neon.magenta)
            .padding(horizontal = 6.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 9) "9+" else count.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Friend's presence dot; [known] false (chat not connected) draws a dashed ring. */
@Composable
fun PresenceDot(presence: Presence?, known: Boolean, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val tone = if (known) Labels.presence(presence).tone else null
    Canvas(modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val r = this.size.minDimension / 2 - stroke / 2
        when (tone) {
            Labels.Tone.MATCH -> {
                drawCircle(Neon.magenta.copy(alpha = 0.35f), radius = r + stroke * 1.5f)
                drawCircle(Neon.magenta, radius = r + stroke / 2)
            }
            Labels.Tone.ONLINE -> {
                drawCircle(Neon.green.copy(alpha = 0.3f), radius = r + stroke * 1.5f)
                drawCircle(Neon.green, radius = r + stroke / 2)
            }
            Labels.Tone.MOBILE -> {
                drawCircle(Neon.cyan.copy(alpha = 0.25f), radius = r)
                drawCircle(Neon.cyan, radius = r, style = Stroke(stroke))
            }
            Labels.Tone.OFFLINE -> drawCircle(Neon.muted.copy(alpha = 0.5f), radius = r, style = Stroke(stroke))
            null -> drawCircle(
                Neon.muted.copy(alpha = 0.5f),
                radius = r,
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 1.2f, stroke))),
            )
        }
    }
}
