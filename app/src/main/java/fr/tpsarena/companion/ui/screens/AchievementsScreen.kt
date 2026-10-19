package fr.tpsarena.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.tpsarena.companion.data.Format
import fr.tpsarena.companion.data.Labels
import fr.tpsarena.companion.data.Progression
import fr.tpsarena.companion.ui.Resource
import fr.tpsarena.companion.ui.SessionContext
import fr.tpsarena.companion.ui.components.CachedNote
import fr.tpsarena.companion.ui.components.Muted
import fr.tpsarena.companion.ui.components.NeonIcons
import fr.tpsarena.companion.ui.components.Note
import fr.tpsarena.companion.ui.components.NoteKind
import fr.tpsarena.companion.ui.components.Panel
import fr.tpsarena.companion.ui.components.ScreenTitle
import fr.tpsarena.companion.ui.theme.Neon

class AchievementsModel(ctx: SessionContext) : ViewModel() {
    val progression = Resource(viewModelScope, ctx, "progression", Progression.serializer()) {
        ctx.api.get<Progression>("/progression/${ctx.user.userId}")
    }
}

@Composable
fun AchievementsScreen(ctx: SessionContext) {
    val model = viewModel { AchievementsModel(ctx) }
    WithData(model.progression) { state, data ->
        // Unlocked first (newest to oldest), then locked in the API's order.
        val unlocked = data.achievements.filter { it.unlockedAt != null }.sortedByDescending { Format.epochMillis(it.unlockedAt!!) }
        val locked = data.achievements.filter { it.unlockedAt == null }
        ScreenList {
            item { CachedNote(state.cachedAt, state.loading) }
            state.error?.let { item { Note(it, kind = NoteKind.ERROR) } }
            item { ScreenTitle("Succès", trailing = "${unlocked.size} / ${data.achievements.size}") }
            items(unlocked + locked, key = { it.id }) { a ->
                val text = Labels.achievementText(a.id, a.name, a.description)
                val done = a.unlockedAt != null
                Panel(border = if (done) Neon.green.copy(alpha = 0.35f) else Neon.panelBorder) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (done) NeonIcons.Check else NeonIcons.Lock,
                            contentDescription = if (done) "Débloqué" else "Verrouillé",
                            tint = if (done) Neon.green else Neon.muted,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(Modifier.weight(1f).alpha(if (done) 1f else 0.75f)) {
                            Text(text.name, style = MaterialTheme.typography.titleMedium)
                            Text(text.description, style = MaterialTheme.typography.bodyLarge)
                            Muted(if (done) "Débloqué le ${Format.date(a.unlockedAt)}" else "Verrouillé")
                        }
                    }
                }
            }
        }
    }
}
