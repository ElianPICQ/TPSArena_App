package fr.tpsarena.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.tpsarena.companion.CompanionApp
import fr.tpsarena.companion.ui.Brand
import fr.tpsarena.companion.ui.components.Muted
import fr.tpsarena.companion.ui.components.NeonButton
import fr.tpsarena.companion.ui.components.NeonTextField
import fr.tpsarena.companion.ui.components.Note
import fr.tpsarena.companion.ui.components.NoteKind
import fr.tpsarena.companion.ui.components.Panel
import fr.tpsarena.companion.ui.theme.Neon
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(app: CompanionApp, notice: String?) {
    var server by rememberSaveable { mutableStateOf(app.settings.serverUrl.orEmpty()) }
    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val canSubmit = !pending && server.isNotBlank() && identifier.isNotBlank() && password.isNotEmpty()

    fun submit() {
        if (!canSubmit) return
        pending = true
        error = null
        focus.clearFocus()
        scope.launch {
            try {
                app.session.login(server, identifier.trim(), password)
            } catch (e: Exception) {
                error = e.message ?: "Erreur inattendue."
                pending = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Brand(fontSize = 30)
        Spacer(Modifier.height(8.dp))
        Text("COMPANION", style = MaterialTheme.typography.labelSmall, color = Neon.magenta)
        Spacer(Modifier.height(6.dp))
        Text("Tes stats, partout", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Panel(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                (error ?: notice)?.let { Note(it, kind = NoteKind.ERROR) }
                NeonTextField(
                    server,
                    { server = it },
                    Modifier.fillMaxWidth(),
                    label = "Serveur",
                    placeholder = "192.168.1.20:8443",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                )
                NeonTextField(
                    identifier,
                    { identifier = it },
                    Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
                    label = "E-mail ou pseudo",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                )
                NeonTextField(
                    password,
                    { password = it },
                    Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                    label = "Mot de passe",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Masquer" else "Afficher", color = Neon.cyan, fontWeight = FontWeight.Bold)
                        }
                    },
                )
                NeonButton(
                    if (pending) "Connexion…" else "Se connecter",
                    onClick = ::submit,
                    modifier = Modifier.fillMaxWidth(),
                    primary = true,
                    enabled = canSubmit,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Muted(
            "Serveur : l’adresse du site TPS Arena sur le réseau local (port HTTPS 8443 par défaut, ou http://…:8080).",
            Modifier.widthIn(max = 480.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Muted(
            "Pas encore de compte ? Crée-le dans le jeu, puis reviens ici avec les mêmes identifiants.",
            Modifier.widthIn(max = 480.dp),
            textAlign = TextAlign.Center,
        )
    }
}
