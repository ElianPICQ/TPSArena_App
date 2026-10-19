package fr.tpsarena.companion.data

// Displayed texts: error messages (by the API's `error` code, same as the game's
// ApiClient.ERROR_MESSAGES and the PWA's lib/labels.ts), map names (the game's MapCatalog) and
// achievements (backend/services/stats/stats/progression.py, in English on the API side).

object Labels {
    private val ERROR_MESSAGES = mapOf(
        "unreachable" to "Impossible de joindre le serveur. Vérifie ta connexion au réseau.",
        "bad_gateway" to "Le serveur a renvoyé une réponse invalide. Réessaie dans un instant.",
        "web_bff_unavailable" to "Le service de l’app est indisponible. Réessaie dans un instant.",
        "auth_unavailable" to "Le service des comptes est indisponible. Réessaie dans un instant.",
        "stats_unavailable" to "Les statistiques sont indisponibles. Réessaie dans un instant.",
        "rate_limited" to "Trop de tentatives. Patiente un peu avant de réessayer.",
        "validation" to "Certaines informations sont invalides.",
        "invalid_credentials" to "Identifiant ou mot de passe incorrect.",
        "email_not_verified" to "Vérifie d’abord ton adresse e-mail avec le code reçu, depuis le jeu.",
        "invalid_token" to "Ta session a expiré. Reconnecte-toi.",
        "token_expired" to "Ta session a expiré. Reconnecte-toi.",
        "not_authenticated" to "Ta session a expiré. Reconnecte-toi.",
        "invalid_refresh_token" to "Ta session a expiré. Reconnecte-toi.",
        "match_not_found" to "Cette partie n’existe pas.",
        "social_unavailable" to "Le service des amis est indisponible. Réessaie dans un instant.",
        "realtime_unavailable" to "Le chat est indisponible. Réessaie dans un instant.",
        "user_not_found" to "Ce joueur n’existe pas.",
        "cannot_friend_self" to "Tu ne peux pas t’ajouter toi-même.",
        "already_friends" to "Vous êtes déjà amis.",
        "request_exists" to "Tu as déjà envoyé une demande à ce joueur.",
        "request_not_found" to "Cette demande d’ami n’existe plus.",
        "too_many_friends" to "Nombre maximal d’amis atteint.",
        "not_friends" to "Vous n’êtes pas amis.",
        "invalid_cursor" to "Impossible de charger la suite de l’historique. Recharge la page.",
        // The app's own codes (not sent by the API).
        "no_server" to "Indique l’adresse du serveur TPS Arena.",
        "invalid_server_url" to "Adresse du serveur invalide (exemple : 192.168.1.20:8443).",
        "untrusted_certificate" to
            "Certificat du serveur non reconnu : installe la CA du site sur le téléphone, ou utilise une adresse en http://.",
    )

    fun messageFor(code: String, status: Int? = null): String =
        ERROR_MESSAGES[code] ?: if (status != null) "Erreur inattendue (code $status)." else "Erreur inattendue."

    private val MAPS = mapOf(
        "neo-edo" to "NEO-EDO",
        "hikari-mall" to "Hikari Mall",
    )

    fun mapName(id: String): String = MAPS[id] ?: id

    data class AchievementText(val name: String, val description: String)

    private val ACHIEVEMENTS = mapOf(
        "first_match" to AchievementText("Bienvenue dans l’arène", "Jouer sa première partie."),
        "first_win" to AchievementText("Premier bain de sang", "Gagner une partie."),
        "wins_10" to AchievementText("Prétendant", "Gagner 10 parties."),
        "kills_100" to AchievementText("Tireur d’élite", "Faire 100 kills."),
        "kills_1000" to AchievementText("Légende", "Faire 1000 kills."),
        "match_10_kills" to AchievementText("Carnage", "Faire 10 kills dans une seule partie."),
        "flawless" to AchievementText("Intouchable", "Gagner une partie sans mourir."),
    )

    /** French name and description; the API's for an achievement added since. */
    fun achievementText(id: String, name: String, description: String): AchievementText =
        ACHIEVEMENTS[id] ?: AchievementText(name, description)

    data class BoardLabel(val tab: String, val unit: String)

    fun board(type: BoardType): BoardLabel = when (type) {
        BoardType.XP -> BoardLabel("XP", "XP")
        BoardType.WINS -> BoardLabel("Victoires", "victoires")
        BoardType.KILLS -> BoardLabel("Kills", "kills")
    }

    /** Presence label under a friend's name, and its tone (dot color, sort order). */
    enum class Tone { MATCH, ONLINE, MOBILE, OFFLINE }

    data class PresenceLabel(val text: String, val tone: Tone)

    fun presence(p: Presence?): PresenceLabel = when {
        p == null || p.status == "offline" -> PresenceLabel("Hors ligne", Tone.OFFLINE)
        p.activity == "in_match" -> PresenceLabel("En partie", Tone.MATCH)
        p.activity == "mobile" -> PresenceLabel("Sur mobile", Tone.MOBILE)
        else -> PresenceLabel("Dans le menu", Tone.ONLINE)
    }
}
