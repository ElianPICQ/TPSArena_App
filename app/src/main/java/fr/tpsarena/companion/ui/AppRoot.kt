package fr.tpsarena.companion.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.tpsarena.companion.AuthState
import fr.tpsarena.companion.CompanionApp
import fr.tpsarena.companion.realtime.Routes
import fr.tpsarena.companion.ui.components.Badge
import fr.tpsarena.companion.ui.components.Loading
import fr.tpsarena.companion.ui.components.NeonBackground
import fr.tpsarena.companion.ui.components.NeonIcons
import fr.tpsarena.companion.ui.screens.AchievementsScreen
import fr.tpsarena.companion.ui.screens.ChatScreen
import fr.tpsarena.companion.ui.screens.FriendsScreen
import fr.tpsarena.companion.ui.screens.HistoryScreen
import fr.tpsarena.companion.ui.screens.LeaderboardsScreen
import fr.tpsarena.companion.ui.screens.LoginScreen
import fr.tpsarena.companion.ui.screens.MatchDetailScreen
import fr.tpsarena.companion.ui.screens.ProfileScreen
import fr.tpsarena.companion.ui.theme.Neon
import fr.tpsarena.companion.ui.theme.Orbitron
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppRoot(app: CompanionApp, pendingRoute: StateFlow<String?>, consumeRoute: () -> Unit) {
    val auth by app.session.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        NeonBackground()
        when (val state = auth) {
            AuthState.Checking -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.Center) {
                Loading("Connexion…")
            }
            is AuthState.Anonymous -> LoginScreen(app, notice = state.message)
            is AuthState.SignedIn -> key(state.user.userId, state.online) {
                val ctx = SessionContext(state.user, state.online, app.api, app.cache)
                SignedIn(app, ctx, pendingRoute, consumeRoute)
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.PROFILE, "Profil", NeonIcons.Profile),
    Tab(Routes.ACHIEVEMENTS, "Succès", NeonIcons.Trophy),
    Tab(Routes.HISTORY, "Matchs", NeonIcons.History),
    Tab(Routes.LEADERBOARDS, "Classement", NeonIcons.Podium),
    Tab(Routes.FRIENDS, "Amis", NeonIcons.Friends),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignedIn(app: CompanionApp, ctx: SessionContext, pendingRoute: StateFlow<String?>, consumeRoute: () -> Unit) {
    val model: SignedInModel = viewModel(key = "${ctx.user.userId}-${ctx.online}") { SignedInModel(ctx, app.realtime) }
    val nav = rememberNavController()
    val rt by app.realtime.state.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val activeTab = when {
        current == null -> Routes.PROFILE
        current.startsWith("match/") -> Routes.HISTORY
        current.startsWith("chat/") -> Routes.FRIENDS
        else -> current
    }

    AskNotificationsOnce(app)

    // Tapped notification: open its screen.
    val route by pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(route) {
        val target = route ?: return@LaunchedEffect
        consumeRoute()
        navigate(nav, target)
    }

    // Keyboard open: the tab bar hides and the screen ends above the keyboard (chat input).
    val imeVisible = WindowInsets.isImeVisible
    Column(Modifier.fillMaxSize().imePadding()) {
        Header(app, ctx)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NavHost(nav, startDestination = Routes.PROFILE, modifier = Modifier.fillMaxSize()) {
                composable(Routes.PROFILE) { ProfileScreen(ctx, onAchievements = { navigate(nav, Routes.ACHIEVEMENTS) }) }
                composable(Routes.ACHIEVEMENTS) { AchievementsScreen(ctx) }
                composable(Routes.HISTORY) { HistoryScreen(ctx, onMatch = { nav.navigate(Routes.match(it)) }) }
                composable("match/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    MatchDetailScreen(ctx, entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                }
                composable(Routes.LEADERBOARDS) { LeaderboardsScreen(ctx) }
                composable(Routes.FRIENDS) { FriendsScreen(ctx, model, app.realtime, onChat = { nav.navigate(Routes.chat(it)) }) }
                composable("chat/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                    ChatScreen(
                        ctx,
                        model,
                        app.realtime,
                        app.notifier,
                        entry.arguments?.getString("id").orEmpty().lowercase(),
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            Toasts(
                toasts = rt.toasts,
                onOpen = { toast ->
                    app.realtime.dismissToast(toast.id)
                    toast.route?.let { navigate(nav, it) }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (activeTab == Routes.FRIENDS && current?.startsWith("chat/") == true) 76.dp else 12.dp),
            )
        }
        if (!imeVisible) TabBar(activeTab, badge = rt.unread.values.sum() + rt.incoming, onSelect = { navigate(nav, it) })
    }
}

/** Tabs keep one copy of each screen; a chat or match opens on top of its tab. */
private fun navigate(nav: NavHostController, route: String) {
    val tab = TABS.firstOrNull { it.route == route }
    if (tab != null) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = false }
            launchSingleTop = true
        }
        return
    }
    val parent = when {
        route.startsWith("chat/") -> Routes.FRIENDS
        route.startsWith("match/") -> Routes.HISTORY
        else -> null
    }
    if (parent != null) navigate(nav, parent)
    nav.navigate(route) { launchSingleTop = true }
}

@Composable
private fun Header(app: CompanionApp, ctx: SessionContext) {
    var menu by remember { mutableStateOf(false) }
    var backgroundChat by remember { mutableStateOf(app.settings.backgroundChat) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Neon.bg.copy(alpha = 0.9f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .drawBehind { drawLine(Neon.panelBorder, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Brand(fontSize = 17)
            Text(
                ctx.user.username,
                Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(NeonIcons.More, contentDescription = "Menu", tint = Neon.muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Neon.panelLight) {
                    Text(
                        "Serveur : ${app.settings.serverUrl.orEmpty().substringAfter("://")}",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neon.muted,
                    )
                    DropdownMenuItem(
                        text = { Text("Notifications en arrière-plan") },
                        leadingIcon = {
                            Checkbox(
                                checked = backgroundChat,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = Neon.cyan, checkmarkColor = Neon.bg),
                            )
                        },
                        onClick = {
                            backgroundChat = !backgroundChat
                            app.settings.backgroundChat = backgroundChat
                            app.updateChatService()
                        },
                    )
                    HorizontalDivider(color = Neon.panelBorder)
                    DropdownMenuItem(
                        text = { Text("Se déconnecter") },
                        leadingIcon = { Icon(NeonIcons.Logout, contentDescription = null, tint = Neon.muted) },
                        onClick = {
                            menu = false
                            app.session.logout()
                        },
                    )
                }
            }
        }
        if (!ctx.online) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Neon.yellow.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hors ligne : dernières données gardées.", color = Neon.yellow, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.size(12.dp))
                Text(
                    "Réessayer",
                    Modifier.clickable { app.session.resume() }.padding(4.dp),
                    color = Neon.cyan,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** « TPS ARENA » in Orbitron, ARENA in cyan (`.nav__brand`). */
@Composable
fun Brand(fontSize: Int, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append("TPS ")
            withStyle(SpanStyle(color = Neon.cyan)) { append("ARENA") }
        },
        modifier.semantics { contentDescription = "TPS Arena" },
        fontFamily = Orbitron,
        fontWeight = FontWeight.ExtraBold,
        fontSize = fontSize.sp,
        letterSpacing = (fontSize * 0.08).sp,
        color = Neon.text,
    )
}

@Composable
private fun TabBar(active: String, badge: Int, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Neon.bg.copy(alpha = 0.94f))
            .drawBehind { drawLine(Neon.panelBorder, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        for (tab in TABS) {
            val selected = tab.route == active
            val color = if (selected) Neon.cyan else Neon.muted
            Column(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .semantics { this.selected = selected }
                    .clickable { onSelect(tab.route) }
                    .drawBehind { if (selected) drawLine(Neon.cyan, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx()) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box {
                    Icon(tab.icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    if (tab.route == Routes.FRIENDS && badge > 0) {
                        Badge(badge, Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-6).dp), description = "$badge nouveauté(s)")
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(tab.label, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** Socket notifications (message, friend request, achievement…), above the tabs. */
@Composable
private fun Toasts(toasts: List<fr.tpsarena.companion.realtime.Toast>, onOpen: (fr.tpsarena.companion.realtime.Toast) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (toast in toasts) {
            key(toast.id) {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                AnimatedVisibility(shown, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut()) {
                    Text(
                        toast.text,
                        Modifier
                            .fillMaxWidth()
                            .background(Neon.panel.copy(alpha = 0.97f), RoundedCornerShape(4.dp))
                            .border(1.dp, Neon.cyan, RoundedCornerShape(4.dp))
                            .clickable { onOpen(toast) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/** Android 13+: asks once for the notification permission, after the first sign-in. */
@Composable
private fun AskNotificationsOnce(app: CompanionApp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { app.updateChatService() }
    LaunchedEffect(Unit) {
        if (!app.settings.askedNotifications && !app.notifier.canNotify()) {
            app.settings.askedNotifications = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
