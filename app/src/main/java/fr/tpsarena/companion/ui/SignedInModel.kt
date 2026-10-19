package fr.tpsarena.companion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.tpsarena.companion.data.FriendsListing
import fr.tpsarena.companion.realtime.Realtime
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * State shared by the signed-in screens (the PWA's lib/friends.ts): the friend list and
 * requests, loaded once for the whole app (tab badge, usernames in notifications, Friends and
 * Chat screens) and reloaded on every friendship notification of the socket.
 */
class SignedInModel(val ctx: SessionContext, private val realtime: Realtime) : ViewModel() {
    val friends = Resource(viewModelScope, ctx, "friends", FriendsListing.serializer()) { ctx.api.get<FriendsListing>("/friends") }

    init {
        viewModelScope.launch {
            realtime.state.distinctUntilChangedBy { it.friendsVersion }.drop(1).collect {
                if (it.friendsVersion > 0) friends.reload()
            }
        }
        viewModelScope.launch {
            friends.state.map { it.data }.filterNotNull().collect { data ->
                val names = (data.friends.map { it.userId to it.username } +
                    data.incoming.map { it.userId to it.username } +
                    data.outgoing.map { it.userId to it.username })
                    .mapNotNull { (id, name) -> name?.let { id to it } }
                    .toMap()
                realtime.setFriends(names, data.incoming.size)
            }
        }
    }
}
