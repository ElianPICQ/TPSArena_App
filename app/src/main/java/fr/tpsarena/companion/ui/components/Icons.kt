package fr.tpsarena.companion.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Line icons of the PWA (app/src/components/Icons.tsx): 24 × 24, 1.8 stroke, round caps.
// Circles and rectangles are written as paths. Icon() tints them.
object NeonIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in paths) {
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    val Profile = icon("profile", circle(12f, 8f, 4f), "M4 20c1.5-3.5 4.5-5 8-5s6.5 1.5 8 5")
    val Trophy = icon("trophy", "M8 4h8v5a4 4 0 0 1-8 0zM8 6H4.5a3.5 3.5 0 0 0 3.6 4M16 6h3.5a3.5 3.5 0 0 1-3.6 4M12 13v4M8 20h8M9.5 17h5")
    val History = icon("history", "M3.5 12a8.5 8.5 0 1 0 2.5-6M3.5 4v4h4", "M12 7.5V12l3 2")
    val Podium = icon("podium", "M9 20V8h6v12M3 20v-7h6M15 20v-9h6v9M2 20h20")
    val Lock = icon("lock", "M6.5 11h11a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 1-1.5 1.5h-11a1.5 1.5 0 0 1-1.5-1.5v-6a1.5 1.5 0 0 1 1.5-1.5z", "M8 11V8a4 4 0 0 1 8 0v3")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7")
    val Logout = icon("logout", "M10 4H5v16h5M14 8l4 4-4 4M18 12H9")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Friends = icon(
        "friends",
        circle(9f, 8f, 3.5f),
        "M2.5 19.5c1-3.2 3.5-4.8 6.5-4.8s5.5 1.6 6.5 4.8",
        "M15.5 4.8a3.5 3.5 0 0 1 0 6.4M17.5 14.9c2 .7 3.3 2.2 4 4.6",
    )
    val More = icon("more", "M12 5.5v.01M12 12v.01M12 18.5v.01")
    val Send = icon("send", "M4 12l16-8-6 16-3-7z", "M11 13l9-9")
}
