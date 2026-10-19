package fr.tpsarena.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.tpsarena.companion.data.Format
import fr.tpsarena.companion.data.Labels
import fr.tpsarena.companion.data.MatchPage
import fr.tpsarena.companion.data.PlayerMatch
import fr.tpsarena.companion.ui.Resource
import fr.tpsarena.companion.ui.SessionContext
import fr.tpsarena.companion.ui.components.CachedNote
import fr.tpsarena.companion.ui.components.Empty
import fr.tpsarena.companion.ui.components.Muted
import fr.tpsarena.companion.ui.components.NeonButton
import fr.tpsarena.companion.ui.components.Note
import fr.tpsarena.companion.ui.components.NoteKind
import fr.tpsarena.companion.ui.components.Panel
import fr.tpsarena.companion.ui.components.ScreenTitle
import fr.tpsarena.companion.ui.theme.Neon
import fr.tpsarena.companion.ui.theme.Orbitron
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE = 20

class HistoryModel(private val ctx: SessionContext) : ViewModel() {
    // Only the first page is kept for offline use.
    val first = Resource(viewModelScope, ctx, "history", MatchPage.serializer()) {
        ctx.api.get<MatchPage>("/players/${ctx.user.userId}/matches?limit=$PAGE")
    }

    data class More(val matches: List<PlayerMatch> = emptyList(), val cursor: String? = null, val loading: Boolean = false, val error: String? = null)

    val more = MutableStateFlow(More())

    init {
        viewModelScope.launch {
            first.state.collect { s -> more.value = More(cursor = s.data?.nextCursor) }
        }
    }

    fun loadMore() {
        val cursor = more.value.cursor ?: return
        if (more.value.loading) return
        more.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = ctx.api.get<MatchPage>("/players/${ctx.user.userId}/matches?limit=$PAGE&before=${java.net.URLEncoder.encode(cursor, "UTF-8")}")
                more.update { it.copy(matches = it.matches + page.matches, cursor = page.nextCursor, loading = false) }
            } catch (e: Exception) {
                more.update { it.copy(loading = false, error = e.message ?: "Erreur inattendue.") }
            }
        }
    }
}

@Composable
fun HistoryScreen(ctx: SessionContext, onMatch: (String) -> Unit) {
    val model = viewModel { HistoryModel(ctx) }
    val more by model.more.collectAsStateWithLifecycle()
    WithData(model.first) { state, data ->
        val matches = data.matches + more.matches
        ScreenList {
            item { CachedNote(state.cachedAt, state.loading) }
            state.error?.let { item { Note(it, kind = NoteKind.ERROR) } }
            item { ScreenTitle("Historique") }
            if (matches.isEmpty()) {
                item { Empty("Aucune partie terminée pour l’instant. Seules les parties sur un serveur dédié comptent.") }
            }
            items(matches, key = { it.matchId }) { MatchRow(it, onClick = { onMatch(it.matchId) }) }
            more.error?.let { item { Note(it, kind = NoteKind.ERROR) } }
            if (more.cursor != null && ctx.online && state.cachedAt == null) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        NeonButton(if (more.loading) "Chargement…" else "Parties plus anciennes", model::loadMore, enabled = !more.loading)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchRow(match: PlayerMatch, onClick: () -> Unit) {
    val win = match.placement == 1
    Panel(
        border = if (win) Neon.yellow.copy(alpha = 0.5f) else Neon.panelBorder,
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(60.dp)) {
                Text(Format.ordinal(match.placement), fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = if (win) Neon.yellow else Neon.text)
                Muted("/ ${match.playerCount}")
            }
            Column(Modifier.weight(1f)) {
                Text(Labels.mapName(match.map), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted(Format.dateTime(match.endedAt))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Neon.cyan)) { append(match.kills.toString()) }
                        append(" K · ")
                        withStyle(SpanStyle(color = Neon.magenta)) { append(match.deaths.toString()) }
                        append(" M")
                    },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("+${match.xpGained} XP", color = Neon.green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
