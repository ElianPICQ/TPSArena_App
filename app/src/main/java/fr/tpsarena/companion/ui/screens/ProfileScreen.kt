package fr.tpsarena.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.tpsarena.companion.data.Format
import fr.tpsarena.companion.data.Labels
import fr.tpsarena.companion.data.PlayerStats
import fr.tpsarena.companion.data.Progression
import fr.tpsarena.companion.ui.SessionContext
import fr.tpsarena.companion.ui.Resource
import fr.tpsarena.companion.ui.components.CachedNote
import fr.tpsarena.companion.ui.components.Muted
import fr.tpsarena.companion.ui.components.Note
import fr.tpsarena.companion.ui.components.NoteKind
import fr.tpsarena.companion.ui.components.Panel
import fr.tpsarena.companion.ui.components.ScreenTitle
import fr.tpsarena.companion.ui.theme.Neon
import fr.tpsarena.companion.ui.theme.Orbitron
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class ProfileData(val stats: PlayerStats, val progression: Progression)

class ProfileModel(ctx: SessionContext) : ViewModel() {
    val profile = Resource(viewModelScope, ctx, "profile", ProfileData.serializer()) {
        coroutineScope {
            val stats = async { ctx.api.get<PlayerStats>("/players/${ctx.user.userId}/stats") }
            val progression = async { ctx.api.get<Progression>("/progression/${ctx.user.userId}") }
            ProfileData(stats.await(), progression.await())
        }
    }
}

@Composable
fun ProfileScreen(ctx: SessionContext, onAchievements: () -> Unit) {
    val model = viewModel { ProfileModel(ctx) }
    WithData(model.profile) { state, data ->
        val (stats, progression) = data
        // xp_to_next_level: XP of the whole level (L to L + 1), not what is left to earn.
        val levelSpan = progression.xpToNextLevel
        val remaining = maxOf(0L, levelSpan - progression.xpInLevel)
        val fraction = if (levelSpan > 0) (progression.xpInLevel.toFloat() / levelSpan).coerceIn(0f, 1f) else 0f
        val unlocked = progression.achievements.filter { it.unlockedAt != null }.sortedByDescending { Format.epochMillis(it.unlockedAt!!) }
        val latest = unlocked.firstOrNull()
        val winRate = if (stats.matchesPlayed > 0) (stats.wins * 100.0 / stats.matchesPlayed).roundToInt() else 0

        ScreenList {
            item { CachedNote(state.cachedAt, state.loading) }
            state.error?.let { item { Note(it, kind = NoteKind.ERROR) } }
            item {
                Panel(border = Neon.magenta.copy(alpha = 0.4f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LevelBadge(progression.level)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = Neon.cyan, fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                                        append(Format.number(progression.xpInLevel))
                                    }
                                    append(" / ${Format.number(levelSpan)} XP")
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            XpBar(fraction, "Progression vers le niveau ${progression.level + 1}")
                            Muted("Encore ${Format.number(remaining)} XP pour le niveau ${progression.level + 1} · ${Format.number(progression.xp)} XP au total")
                        }
                    }
                }
            }
            item { ScreenTitle("Statistiques") }
            if (stats.matchesPlayed == 0) {
                item { Note("Aucune partie terminée pour l’instant : lance une partie en ligne pour remplir ton profil.") }
            }
            item {
                val tiles = listOf(
                    Triple("Parties", Format.number(stats.matchesPlayed), null),
                    Triple("Victoires", Format.number(stats.wins), Neon.yellow),
                    Triple("Kills", Format.number(stats.kills), Neon.cyan),
                    Triple("Morts", Format.number(stats.deaths), Neon.magenta),
                    Triple("Ratio K/D", Format.ratio(stats.kdRatio), null),
                    Triple("Taux de victoire", "$winRate %", null),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (row in tiles.chunked(2)) {
                        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for ((label, value, accent) in row) Tile(label, value, accent, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
            item {
                Panel(light = true, onClick = onAchievements) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Succès : ${unlocked.size} / ${progression.achievements.size}", style = MaterialTheme.typography.titleMedium)
                            if (latest != null) {
                                val name = Labels.achievementText(latest.id, latest.name, latest.description).name
                                Muted("Dernier : $name, le ${Format.date(latest.unlockedAt!!)}")
                            }
                        }
                        Text("›", color = Neon.cyan, fontSize = 26.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelBadge(level: Int) {
    Column(
        Modifier
            .size(84.dp)
            .background(Neon.magenta.copy(alpha = 0.08f), CircleShape)
            .border(2.dp, Neon.magenta, CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("NIVEAU", style = MaterialTheme.typography.labelSmall, color = Neon.muted)
        Text(level.toString(), fontFamily = Orbitron, fontWeight = FontWeight.Black, fontSize = 30.sp, color = Neon.magenta)
    }
}

@Composable
private fun XpBar(fraction: Float, description: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Neon.cyan.copy(alpha = 0.06f))
            .border(1.dp, Neon.cyan.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Neon.cyan.copy(alpha = 0.55f), Neon.cyan))),
        )
    }
}

@Composable
private fun Tile(label: String, value: String, accent: Color?, modifier: Modifier) {
    Panel(modifier, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, letterSpacing = 0.8.sp), color = Neon.muted)
        Text(value, Modifier.padding(top = 2.dp), fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = accent ?: Neon.text)
    }
}
