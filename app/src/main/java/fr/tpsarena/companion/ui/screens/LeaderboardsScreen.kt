package fr.tpsarena.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import fr.tpsarena.companion.data.BoardType
import fr.tpsarena.companion.data.Format
import fr.tpsarena.companion.data.Labels
import fr.tpsarena.companion.data.Leaderboard
import fr.tpsarena.companion.ui.Resource
import fr.tpsarena.companion.ui.SessionContext
import fr.tpsarena.companion.ui.components.CachedNote
import fr.tpsarena.companion.ui.components.Empty
import fr.tpsarena.companion.ui.components.ErrorState
import fr.tpsarena.companion.ui.components.Loading
import fr.tpsarena.companion.ui.components.Muted
import fr.tpsarena.companion.ui.components.Note
import fr.tpsarena.companion.ui.components.NoteKind
import fr.tpsarena.companion.ui.components.ScreenTitle
import fr.tpsarena.companion.ui.theme.Neon
import fr.tpsarena.companion.ui.theme.Orbitron
import kotlinx.coroutines.flow.MutableStateFlow

class LeaderboardsModel(private val ctx: SessionContext) : ViewModel() {
    data class Choice(val type: BoardType = BoardType.XP, val top: Boolean = false)

    val choice = MutableStateFlow(Choice())
    val board = MutableStateFlow(resourceFor(choice.value))

    fun choose(next: Choice) {
        if (next == choice.value) return
        choice.value = next
        board.value = resourceFor(next)
    }

    private fun resourceFor(c: Choice): Resource<Leaderboard> {
        val key = "board.${c.type.id}.${if (c.top) "top" else "around"}"
        return Resource(viewModelScope, ctx, key, Leaderboard.serializer()) {
            if (c.top) {
                ctx.api.get<Leaderboard>("/leaderboards/${c.type.id}?limit=50")
            } else {
                ctx.api.get<Leaderboard>("/leaderboards/${c.type.id}/around/${ctx.user.userId}?radius=5")
            }
        }
    }
}

@Composable
fun LeaderboardsScreen(ctx: SessionContext) {
    val model = viewModel { LeaderboardsModel(ctx) }
    val choice by model.choice.collectAsStateWithLifecycle()
    val resource by model.board.collectAsStateWithLifecycle()
    val state by resource.state.collectAsStateWithLifecycle()
    val data = state.data
    val unit = Labels.board(choice.type).unit

    ScreenList {
        item { ScreenTitle("Classements") }
        item {
            Segmented(BoardType.entries.map { Labels.board(it).tab }, BoardType.entries.indexOf(choice.type)) {
                model.choose(choice.copy(type = BoardType.entries[it]))
            }
        }
        item {
            Segmented(listOf("Autour de moi", "Top 50"), if (choice.top) 1 else 0) { model.choose(choice.copy(top = it == 1)) }
        }
        when {
            data == null && state.loading -> item { Loading() }
            data == null -> item { ErrorState(state.error ?: "Erreur inattendue.", onRetry = resource::reload) }
            else -> {
                item { CachedNote(state.cachedAt, state.loading) }
                state.error?.let { item { Note(it, kind = NoteKind.ERROR) } }
                when {
                    !choice.top && data.playerRank == null -> item { Empty("Termine une partie en ligne pour apparaître au classement.") }
                    data.entries.isEmpty() -> item { Empty("Personne n’est encore classé.") }
                    else -> {
                        item {
                            val rank = data.playerRank
                            Muted(
                                if (!choice.top && rank != null) {
                                    "Tu es ${Format.ordinal(rank)} sur ${Format.number(data.totalPlayers)} joueurs."
                                } else {
                                    "${Format.number(data.totalPlayers)} joueurs classés."
                                },
                            )
                        }
                        items(data.entries, key = { it.userId }) { e ->
                            val me = e.userId == ctx.user.userId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .background(Neon.panel, RoundedCornerShape(4.dp))
                                    .border(1.dp, if (me) Neon.cyan.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val rankColor = when (e.rank) {
                                    1 -> Neon.yellow
                                    2 -> Neon.text
                                    3 -> Neon.bronze
                                    else -> Neon.muted
                                }
                                Text(e.rank.toString(), Modifier.width(44.dp), fontFamily = Orbitron, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = rankColor)
                                Text(
                                    buildAnnotatedString {
                                        if (e.username != null) {
                                            append(e.username)
                                        } else {
                                            withStyle(SpanStyle(color = Neon.muted)) { append("Joueur inconnu") }
                                        }
                                        if (me) withStyle(SpanStyle(color = Neon.cyan, fontWeight = FontWeight.Medium)) { append(" (toi)") }
                                    },
                                    Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildAnnotatedString {
                                        append(Format.number(e.value))
                                        withStyle(SpanStyle(color = Neon.muted, fontWeight = FontWeight.Medium, fontSize = 14.sp)) { append(" $unit") }
                                    },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size, RoundedCornerShape(4.dp)),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Neon.cyan.copy(alpha = 0.18f),
                    activeContentColor = Neon.cyan,
                    activeBorderColor = Neon.cyan.copy(alpha = 0.6f),
                    inactiveContainerColor = Neon.btnBg,
                    inactiveContentColor = Neon.text,
                    inactiveBorderColor = Neon.btnBorder,
                ),
            ) {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
