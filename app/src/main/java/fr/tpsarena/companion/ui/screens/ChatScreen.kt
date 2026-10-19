package fr.tpsarena.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.tpsarena.companion.data.Format
import fr.tpsarena.companion.data.FriendMessage
import fr.tpsarena.companion.data.Labels
import fr.tpsarena.companion.data.MessagePage
import fr.tpsarena.companion.notify.Notifier
import fr.tpsarena.companion.realtime.ConnectionStatus
import fr.tpsarena.companion.realtime.Realtime
import fr.tpsarena.companion.ui.Resource
import fr.tpsarena.companion.ui.SessionContext
import fr.tpsarena.companion.ui.SignedInModel
import fr.tpsarena.companion.ui.components.CachedNote
import fr.tpsarena.companion.ui.components.ErrorState
import fr.tpsarena.companion.ui.components.Loading
import fr.tpsarena.companion.ui.components.Muted
import fr.tpsarena.companion.ui.components.NeonButton
import fr.tpsarena.companion.ui.components.NeonIcons
import fr.tpsarena.companion.ui.components.NeonTextField
import fr.tpsarena.companion.ui.components.Note
import fr.tpsarena.companion.ui.components.NoteKind
import fr.tpsarena.companion.ui.components.PresenceDot
import fr.tpsarena.companion.ui.theme.Neon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder

private const val PAGE = 30
private const val MAX_LENGTH = 500

class ChatModel(private val ctx: SessionContext, private val friendId: String) : ViewModel() {
    val history = Resource(viewModelScope, ctx, "chat.$friendId", MessagePage.serializer()) {
        ctx.api.get<MessagePage>("/conversations/$friendId/messages?limit=$PAGE")
    }

    data class Older(val messages: List<FriendMessage> = emptyList(), val cursor: String? = null, val loading: Boolean = false, val error: String? = null)

    val older = MutableStateFlow(Older())

    init {
        viewModelScope.launch { history.state.collect { s -> older.value = Older(cursor = s.data?.nextCursor) } }
    }

    fun loadOlder() {
        val cursor = older.value.cursor ?: return
        if (older.value.loading) return
        older.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = ctx.api.get<MessagePage>("/conversations/$friendId/messages?limit=$PAGE&before=${URLEncoder.encode(cursor, "UTF-8")}")
                older.update { it.copy(messages = it.messages + page.messages, cursor = page.nextCursor, loading = false) }
            } catch (e: Exception) {
                older.update { it.copy(loading = false, error = e.message ?: "Erreur inattendue.") }
            }
        }
    }
}

/** Chronological order; ids are increasing integers (as text). */
fun mergeMessages(vararg lists: List<FriendMessage>): List<FriendMessage> {
    val byId = LinkedHashMap<String, FriendMessage>()
    for (list in lists) for (m in list) byId[m.id] = m
    return byId.values.sortedWith(
        compareBy<FriendMessage> { Format.epochMillis(it.createdAt) }.thenBy { it.id.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO },
    )
}

@Composable
fun ChatScreen(
    ctx: SessionContext,
    model: SignedInModel,
    realtime: Realtime,
    notifier: Notifier,
    friendId: String,
    onBack: () -> Unit,
) {
    val chat = viewModel { ChatModel(ctx, friendId) }
    val rt by realtime.state.collectAsStateWithLifecycle()
    val friends by model.friends.state.collectAsStateWithLifecycle()
    val history by chat.history.state.collectAsStateWithLifecycle()
    val older by chat.older.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    // This conversation's messages don't count as unread while it is open.
    DisposableEffect(friendId) {
        realtime.openChat(friendId)
        notifier.cancelChat(friendId)
        onDispose { realtime.openChat(null) }
    }

    val listing = friends.data
    val friend = listing?.friends?.firstOrNull { it.userId == friendId }
    val name = friend?.username ?: listing?.incoming?.firstOrNull { it.userId == friendId }?.username ?: "Joueur"
    val stillFriends = listing == null || friend != null
    val live = ctx.online && rt.status == ConnectionStatus.CONNECTED
    val messages = mergeMessages(older.messages, history.data?.messages.orEmpty(), rt.messages[friendId].orEmpty())
    val lastId = messages.lastOrNull()?.id
    val list = rememberLazyListState()

    // New message: scroll to the bottom (the list is reversed: item 0 is the newest).
    LaunchedEffect(lastId) { if (lastId != null) list.animateScrollToItem(0) }

    fun submit() {
        val body = draft.trim()
        if (body.isEmpty() || sending) return
        sending = true
        sendError = null
        scope.launch {
            try {
                realtime.sendMessage(friendId, body)
                draft = ""
            } catch (e: Exception) {
                sendError = e.message ?: "Erreur inattendue."
            } finally {
                sending = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header: back, presence, name, remove.
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(Neon.panelBorder, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) { Icon(NeonIcons.Back, contentDescription = "Retour aux amis", tint = Neon.cyan) }
            PresenceDot(if (live) rt.presence[friendId] else null, known = live)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted(if (live) Labels.presence(rt.presence[friendId]).text else "Présence indisponible")
            }
            if (ctx.online && friend != null) {
                TextButton(onClick = { confirmRemove = true }) { Text("Retirer", color = Neon.cyan, fontWeight = FontWeight.Bold) }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val data = history.data
            when {
                data == null && history.loading -> Loading()
                data == null -> ErrorState(history.error ?: "Erreur inattendue.", onRetry = chat.history::reload)
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    state = list,
                    reverseLayout = true,
                    contentPadding = PaddingValues(16.dp),
                    // Reversed list: Bottom keeps a short conversation against the input.
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                ) {
                    if (messages.isEmpty()) {
                        item { Muted("Aucun message pour l’instant. Dis bonjour !", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                    }
                    items(messages.asReversed(), key = { it.id }) { m -> Bubble(m, mine = m.fromUserId == ctx.user.userId, name = name) }
                    older.error?.let { item { Note(it, kind = NoteKind.ERROR) } }
                    if (older.cursor != null && ctx.online && history.cachedAt == null) {
                        item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                NeonButton(if (older.loading) "Chargement…" else "Messages plus anciens", chat::loadOlder, small = true, enabled = !older.loading)
                            }
                        }
                    }
                    if (history.error != null) item { Note(history.error!!, kind = NoteKind.ERROR) }
                    item { CachedNote(history.cachedAt, history.loading) }
                }
            }
        }

        // Compose bar.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Neon.bg.copy(alpha = 0.95f))
                .drawBehind { drawLine(Neon.panelBorder, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sendError?.let { Note(it, kind = NoteKind.ERROR) }
            when {
                !stillFriends -> Muted("Vous n’êtes plus amis : la conversation est en lecture seule.", Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
                !ctx.online -> Muted("Hors ligne : envoi impossible.", Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonTextField(
                        draft,
                        { if (it.length <= MAX_LENGTH) draft = it },
                        Modifier.weight(1f).semantics { contentDescription = "Message à $name" },
                        placeholder = if (live) "Message" else "Connexion au chat…",
                        enabled = live,
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                    )
                    val canSend = live && !sending && draft.isNotBlank()
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(Neon.btnBg, RoundedCornerShape(4.dp))
                            .border(1.dp, if (canSend) Neon.cyan.copy(alpha = 0.6f) else Neon.btnBorder, RoundedCornerShape(4.dp))
                            .clickable(enabled = canSend, onClick = ::submit),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(NeonIcons.Send, contentDescription = "Envoyer", tint = if (canSend) Neon.cyan else Neon.muted)
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = Neon.panelLight,
            title = { Text("Retirer $name ?") },
            text = { Text("Vous ne pourrez plus vous écrire.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        try {
                            ctx.api.send("DELETE", "/friends/$friendId")
                            model.friends.reload()
                            onBack()
                        } catch (e: Exception) {
                            sendError = e.message ?: "Erreur inattendue."
                        }
                    }
                }) { Text("Retirer", color = Neon.red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Annuler", color = Neon.text) } },
        )
    }
}

@Composable
private fun Bubble(m: FriendMessage, mine: Boolean, name: String) {
    val shape = if (mine) RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp) else RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(if (mine) Neon.cyan.copy(alpha = 0.12f) else Neon.panel, shape)
                .border(1.dp, if (mine) Neon.cyan.copy(alpha = 0.45f) else Neon.panelBorder, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { contentDescription = "${if (mine) "Toi" else name} : ${m.body}, ${Format.dateTime(m.createdAt)}" },
        ) {
            Text(m.body, style = MaterialTheme.typography.bodyLarge)
            Text(Format.dateTime(m.createdAt), Modifier.align(Alignment.End).padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = Neon.muted)
        }
    }
}
