package fr.tpsarena.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.tpsarena.companion.data.Format
import fr.tpsarena.companion.data.Labels
import fr.tpsarena.companion.data.Match
import fr.tpsarena.companion.ui.Resource
import fr.tpsarena.companion.ui.SessionContext
import fr.tpsarena.companion.ui.components.CachedNote
import fr.tpsarena.companion.ui.components.ErrorState
import fr.tpsarena.companion.ui.components.Loading
import fr.tpsarena.companion.ui.components.Muted
import fr.tpsarena.companion.ui.components.NeonIcons
import fr.tpsarena.companion.ui.components.Note
import fr.tpsarena.companion.ui.components.NoteKind
import fr.tpsarena.companion.ui.components.Panel
import fr.tpsarena.companion.ui.components.ScreenTitle
import fr.tpsarena.companion.ui.theme.Neon
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MatchModel(ctx: SessionContext, matchId: String) : ViewModel() {
    val match = Resource(viewModelScope, ctx, "match.$matchId", Match.serializer()) { ctx.api.get<Match>("/matches/$matchId") }
}

@Composable
fun MatchDetailScreen(ctx: SessionContext, matchId: String, onBack: () -> Unit) {
    val model = viewModel { MatchModel(ctx, matchId) }
    val state by model.match.state.collectAsStateWithLifecycle()
    val data = state.data
    ScreenList {
        item { BackLink("Historique", onBack) }
        when {
            data == null && state.loading -> item { Loading() }
            data == null -> item { ErrorState(state.error ?: "Erreur inattendue.", onRetry = model.match::reload) }
            else -> {
                item { CachedNote(state.cachedAt, state.loading) }
                state.error?.let { item { Note(it, kind = NoteKind.ERROR) } }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ScreenTitle(Labels.mapName(data.map))
                        val n = data.players.size
                        Muted("${Format.dateTime(data.endedAt)} · ${Format.duration(data.startedAt, data.endedAt)} · $n ${if (n > 1) "joueurs" else "joueur"}")
                        Muted("Les bots ne sont pas listés, mais comptent dans les places.")
                    }
                }
                item { Scoreboard(data, ctx.user.userId) }
            }
        }
    }
}

@Composable
private fun Scoreboard(match: Match, me: String) {
    Panel(padding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
        ScoreRow("Place", "Joueur", "K", "M", "XP", header = true)
        HorizontalDivider(color = Neon.panelBorder)
        match.players.forEach { p ->
            val mine = p.userId == me
            ScoreRow(
                Format.ordinal(p.placement),
                p.username ?: if (p.userId != null) "Joueur inconnu" else "Compte supprimé",
                p.kills.toString(),
                p.deaths.toString(),
                "+${p.xpGained}",
                highlight = mine,
                muted = p.username == null,
                suffix = if (mine) " (toi)" else null,
            )
        }
    }
}

@Composable
private fun ScoreRow(
    place: String,
    name: String,
    kills: String,
    deaths: String,
    xp: String,
    header: Boolean = false,
    highlight: Boolean = false,
    muted: Boolean = false,
    suffix: String? = null,
) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge
    val color = if (header) Neon.muted else Neon.text
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlight) Neon.cyan.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(place, Modifier.width(52.dp), style = style, color = color)
        Text(
            name + (suffix ?: ""),
            Modifier.weight(1f),
            style = style,
            color = if (muted) Neon.muted else if (highlight) Neon.cyan else color,
            fontWeight = if (header) null else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        for (value in listOf(kills, deaths)) Text(value, Modifier.width(40.dp), style = style, color = color, textAlign = TextAlign.End)
        Text(xp, Modifier.width(56.dp), style = style, color = color, textAlign = TextAlign.End)
    }
}

@Composable
fun BackLink(label: String, onBack: () -> Unit) {
    Row(
        Modifier.clickable(role = Role.Button, onClick = onBack).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(NeonIcons.Back, contentDescription = null, tint = Neon.cyan, modifier = Modifier.size(20.dp))
        Text(label, color = Neon.cyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
    }
}
