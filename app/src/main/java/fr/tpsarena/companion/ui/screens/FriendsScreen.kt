package fr.tpsarena.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.tpsarena.companion.data.FriendRequestResult
import fr.tpsarena.companion.data.Labels
import fr.tpsarena.companion.data.PublicUser
import fr.tpsarena.companion.data.UserSearch
import fr.tpsarena.companion.realtime.ConnectionStatus
import fr.tpsarena.companion.realtime.Realtime
import fr.tpsarena.companion.ui.SessionContext
import fr.tpsarena.companion.ui.SignedInModel
import fr.tpsarena.companion.ui.components.Badge
import fr.tpsarena.companion.ui.components.CachedNote
import fr.tpsarena.companion.ui.components.Empty
import fr.tpsarena.companion.ui.components.Muted
import fr.tpsarena.companion.ui.components.NeonButton
import fr.tpsarena.companion.ui.components.NeonTextField
import fr.tpsarena.companion.ui.components.Note
import fr.tpsarena.companion.ui.components.NoteKind
import fr.tpsarena.companion.ui.components.Panel
import fr.tpsarena.companion.ui.components.PresenceDot
import fr.tpsarena.companion.ui.components.ScreenTitle
import fr.tpsarena.companion.ui.theme.Neon
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.URLEncoder
import java.text.Collator
import java.util.Locale

// Display order: in a match, in the menu, on mobile, offline; then by username.
private val RANK = mapOf(Labels.Tone.MATCH to 0, Labels.Tone.ONLINE to 1, Labels.Tone.MOBILE to 2, Labels.Tone.OFFLINE to 3)

@Composable
fun FriendsScreen(ctx: SessionContext, model: SignedInModel, realtime: Realtime, onChat: (String) -> Unit) {
    val rt by realtime.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    fun act(key: String, action: suspend () -> Unit) {
        busy = key
        actionError = null
        scope.launch {
            try {
                action()
                model.friends.reload()
            } catch (e: Exception) {
                actionError = e.message ?: "Erreur inattendue."
            } finally {
                busy = null
            }
        }
    }

    WithData(model.friends) { state, data ->
        val live = ctx.online && rt.status == ConnectionStatus.CONNECTED
        val collator = remember { Collator.getInstance(Locale.FRENCH) }
        val sorted = data.friends.sortedWith(
            compareBy<fr.tpsarena.companion.data.Friend> { RANK.getValue(Labels.presence(rt.presence[it.userId]).tone) }
                .thenComparator { a, b -> collator.compare(a.username.orEmpty(), b.username.orEmpty()) },
        )
        val known = (data.friends.map { it.userId } + data.incoming.map { it.userId } + data.outgoing.map { it.userId }).toSet()
        val friendIds = data.friends.map { it.userId }.toSet()

        ScreenList {
            item { CachedNote(state.cachedAt, state.loading) }
            state.error?.let { item { Note(it, kind = NoteKind.ERROR) } }
            item { ScreenTitle("Amis") }
            if (ctx.online && !live) {
                item { Muted(if (rt.status == ConnectionStatus.DISCONNECTED) "Chat déconnecté, nouvelle tentative…" else "Connexion au chat…") }
            }
            actionError?.let { item { Note(it, kind = NoteKind.ERROR) } }
            if (ctx.online) {
                item {
                    AddFriend(ctx, known, friendIds, busy) { target ->
                        act("add-${target.userId}") {
                            ctx.api.send("POST", "/friends/requests", buildJsonObject { put("user_id", JsonPrimitive(target.userId)) }, FriendRequestResult.serializer())
                        }
                    }
                }
            }

            if (data.incoming.isNotEmpty()) {
                item { SectionTitle("Demandes reçues", badge = data.incoming.size) }
                items(data.incoming, key = { "in-${it.requestId}" }) { r ->
                    Panel(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(r.username ?: "Joueur inconnu", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            NeonButton("Accepter", { act(r.requestId) { ctx.api.send("POST", "/friends/requests/${r.requestId}/accept") } }, small = true, primary = true, enabled = ctx.online && busy == null)
                            NeonButton("Refuser", { act(r.requestId) { ctx.api.send("POST", "/friends/requests/${r.requestId}/decline") } }, small = true, enabled = ctx.online && busy == null)
                        }
                    }
                }
            }

            item { SectionTitle("Mes amis", count = data.friends.size) }
            if (sorted.isEmpty()) {
                item { Empty("Pas encore d’amis. Cherche un pseudo ci-dessus pour envoyer une demande.") }
            }
            items(sorted, key = { "f-${it.userId}" }) { f ->
                val presence = if (live) rt.presence[f.userId] else null
                val unread = rt.unread[f.userId] ?: 0
                Panel(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), onClick = { onChat(f.userId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PresenceDot(presence, known = live)
                        Column(Modifier.weight(1f)) {
                            Text(f.username ?: "Joueur inconnu", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Muted(if (live) Labels.presence(presence).text else "Présence indisponible")
                        }
                        if (unread > 0) Badge(unread, description = "$unread message(s) non lu(s)") else Text("›", color = Neon.cyan, fontSize = 26.sp)
                    }
                }
            }

            if (data.outgoing.isNotEmpty()) {
                item { SectionTitle("Demandes envoyées") }
                items(data.outgoing, key = { "out-${it.requestId}" }) { r ->
                    Panel(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(r.username ?: "Joueur inconnu", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Muted("En attente de réponse")
                            }
                            NeonButton("Annuler", { act(r.requestId) { ctx.api.send("POST", "/friends/requests/${r.requestId}/decline") } }, small = true, enabled = ctx.online && busy == null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, badge: Int? = null, count: Int? = null) {
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp))
        if (badge != null) Badge(badge)
        if (count != null) Text(count.toString(), color = Neon.muted, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AddFriend(ctx: SessionContext, known: Set<String>, friendIds: Set<String>, busy: String?, onAdd: (PublicUser) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PublicUser>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    fun search() {
        val name = query.trim()
        if (name.isEmpty() || searching) return
        searching = true
        error = null
        focus.clearFocus()
        scope.launch {
            try {
                val found = ctx.api.get<UserSearch>("/users/search?username=${URLEncoder.encode(name, "UTF-8")}")
                results = found.users.filter { it.userId != ctx.user.userId }
            } catch (e: Exception) {
                error = e.message ?: "Erreur inattendue."
            } finally {
                searching = false
            }
        }
    }

    Panel(light = true) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ajouter un ami", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonTextField(
                    query,
                    { if (it.length <= 20) query = it },
                    Modifier.weight(1f),
                    placeholder = "Pseudo",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                NeonButton(if (searching) "…" else "Chercher", ::search, enabled = !searching && query.isNotBlank())
            }
            error?.let { Note(it, kind = NoteKind.ERROR) }
            results?.let { list ->
                if (list.isEmpty()) {
                    Muted("Aucun joueur trouvé.")
                } else {
                    list.forEach { u ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(u.username, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            when {
                                u.userId in friendIds -> Muted("Déjà ami")
                                u.userId in known -> Muted("Demande en cours")
                                else -> NeonButton(if (busy == "add-${u.userId}") "…" else "Ajouter", { onAdd(u) }, small = true, primary = true, enabled = busy == null)
                            }
                        }
                    }
                }
            }
        }
    }
}
