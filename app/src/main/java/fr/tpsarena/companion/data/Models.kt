package fr.tpsarena.companion.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Responses of the web-bff (backend/services/web-bff), which relays Stats and Social and adds
// usernames from Auth. Mirrors TPSArena_Site/app/src/lib/types.ts.

@Serializable
data class User(
    @SerialName("user_id") val userId: String,
    val username: String,
)

@Serializable
data class Session(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val user: User,
)

@Serializable
data class PlayerStats(
    @SerialName("user_id") val userId: String,
    @SerialName("matches_played") val matchesPlayed: Int,
    val wins: Int,
    val kills: Int,
    val deaths: Int,
    @SerialName("kd_ratio") val kdRatio: Double,
    val level: Int,
    val xp: Long,
)

@Serializable
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    @SerialName("unlocked_at") val unlockedAt: String? = null,
)

@Serializable
data class Progression(
    @SerialName("user_id") val userId: String,
    val level: Int,
    val xp: Long,
    @SerialName("xp_in_level") val xpInLevel: Long,
    /** XP of the whole level (from level to level + 1), not what is left to earn. */
    @SerialName("xp_to_next_level") val xpToNextLevel: Long,
    val achievements: List<Achievement>,
)

@Serializable
data class PlayerMatch(
    @SerialName("match_id") val matchId: String,
    val map: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    val kills: Int,
    val deaths: Int,
    val placement: Int,
    @SerialName("player_count") val playerCount: Int,
    @SerialName("xp_gained") val xpGained: Int,
)

@Serializable
data class MatchPage(
    val matches: List<PlayerMatch>,
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class MatchPlayer(
    @SerialName("user_id") val userId: String? = null, // null: deleted account
    val username: String? = null,
    val kills: Int,
    val deaths: Int,
    val placement: Int,
    @SerialName("xp_gained") val xpGained: Int,
)

@Serializable
data class Match(
    @SerialName("match_id") val matchId: String,
    val map: String,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    val players: List<MatchPlayer>,
)

enum class BoardType(val id: String) { XP("xp"), WINS("wins"), KILLS("kills") }

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    @SerialName("user_id") val userId: String,
    val username: String? = null,
    val value: Long,
)

/** Top board, or the board around the player (then [playerRank] is set, null without a finished match). */
@Serializable
data class Leaderboard(
    val type: String,
    @SerialName("total_players") val totalPlayers: Int,
    val entries: List<LeaderboardEntry>,
    @SerialName("player_rank") val playerRank: Int? = null,
)

// --- Friends (Social, usernames added by the web-bff) and chat (Real-time) ---

@Serializable
data class Friend(
    @SerialName("user_id") val userId: String,
    val since: String,
    val username: String? = null,
)

@Serializable
data class FriendRequest(
    @SerialName("request_id") val requestId: String,
    @SerialName("user_id") val userId: String, // the other player
    @SerialName("created_at") val createdAt: String,
    val username: String? = null,
)

@Serializable
data class FriendsListing(
    val friends: List<Friend>,
    val incoming: List<FriendRequest>,
    val outgoing: List<FriendRequest>,
)

@Serializable
data class FriendRequestResult(
    val status: String, // "pending" | "accepted"
    @SerialName("request_id") val requestId: String? = null,
)

@Serializable
data class PublicUser(
    @SerialName("user_id") val userId: String,
    val username: String,
)

@Serializable
data class UserSearch(val users: List<PublicUser>)

@Serializable
data class Presence(
    @SerialName("user_id") val userId: String,
    val status: String, // "online" | "offline"
    val activity: String? = null, // "menu" | "in_match" | "mobile"
)

@Serializable
data class FriendMessage(
    val id: String,
    @SerialName("from_user_id") val fromUserId: String,
    @SerialName("to_user_id") val toUserId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class MessagePage(
    val messages: List<FriendMessage>, // newest first
    @SerialName("next_cursor") val nextCursor: String? = null,
)
