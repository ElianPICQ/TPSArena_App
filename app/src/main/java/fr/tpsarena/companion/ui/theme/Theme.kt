package fr.tpsarena.companion.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.tpsarena.companion.R

// Colors of the site (TPSArena_Site/src/styles.css), themselves from the game's
// scripts/ui/ui_theme.gd. The app is dark only, like the game and the PWA.
object Neon {
    val bg = Color(0xFF07090F)
    val panel = Color(0xE00F1421) // rgba(15, 20, 33, 0.88)
    val panelBorder = Color(0x292EE3EB) // rgba(46, 227, 235, 0.16)
    val panelLight = Color(0xEB171F30) // rgba(23, 31, 48, 0.92)
    val cyan = Color(0xFF2EE3EB)
    val magenta = Color(0xFFF245C7)
    val yellow = Color(0xFFFFD640)
    val green = Color(0xFF52F28C)
    val red = Color(0xFFFF545C)
    val bronze = Color(0xFFE0915A)
    val text = Color(0xFFE6EDF7)
    val muted = Color(0xFF8C99B3)
    val btnBg = Color(0xFF141C2E)
    val btnBorder = Color(0x4D2EE3EB) // rgba(46, 227, 235, 0.3)
    val fieldBg = Color(0xF2080B13)
}

// Chamfered top-left and bottom-right corners (front/ui/theme/neon_theme.tres): 12 dp for
// panels, 8 dp for buttons, 6 dp for small ones.
val PanelShape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
val ButtonShape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
val SmallButtonShape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)

val Orbitron = FontFamily(
    // Variable font (weights 400 to 900).
    Font(R.font.orbitron, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.orbitron, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(R.font.orbitron, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)

val Rajdhani = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

private val base = TextStyle(fontFamily = Rajdhani, fontWeight = FontWeight.Medium, color = Neon.text)

private val typography = Typography(
    headlineSmall = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.04.em, color = Neon.text),
    titleLarge = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Neon.text),
    titleMedium = base.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    bodyLarge = base.copy(fontSize = 17.sp, lineHeight = 23.sp),
    bodyMedium = base.copy(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = base.copy(fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = base.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 0.03.em),
    labelMedium = base.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelSmall = base.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.12.em),
)

private val colors = darkColorScheme(
    primary = Neon.cyan,
    onPrimary = Neon.bg,
    secondary = Neon.magenta,
    onSecondary = Color.White,
    tertiary = Neon.yellow,
    background = Neon.bg,
    onBackground = Neon.text,
    surface = Neon.panel,
    onSurface = Neon.text,
    surfaceVariant = Neon.panelLight,
    onSurfaceVariant = Neon.muted,
    surfaceContainerHigh = Neon.panelLight,
    surfaceContainer = Neon.panel,
    outline = Neon.btnBorder,
    outlineVariant = Neon.panelBorder,
    error = Neon.red,
)

@Composable
fun TpsArenaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
