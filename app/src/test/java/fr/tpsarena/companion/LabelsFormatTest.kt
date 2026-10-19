package fr.tpsarena.companion

import fr.tpsarena.companion.data.Format
import fr.tpsarena.companion.data.FriendMessage
import fr.tpsarena.companion.data.Labels
import fr.tpsarena.companion.data.Presence
import fr.tpsarena.companion.ui.screens.mergeMessages
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class LabelsFormatTest {
    private lateinit var zone: TimeZone

    @Before
    fun setZone() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(zone)

    @Test
    fun errorMessagesMatchThePwa() {
        assertEquals("Identifiant ou mot de passe incorrect.", Labels.messageFor("invalid_credentials"))
        assertEquals("Erreur inattendue (code 418).", Labels.messageFor("teapot", 418))
        assertEquals("Erreur inattendue.", Labels.messageFor("teapot"))
    }

    @Test
    fun mapsAndAchievements() {
        assertEquals("NEO-EDO", Labels.mapName("neo-edo"))
        assertEquals("new-map", Labels.mapName("new-map"))
        assertEquals("Intouchable", Labels.achievementText("flawless", "Flawless", "x").name)
        // Unknown achievement: the API's English texts.
        assertEquals(Labels.AchievementText("New", "Desc"), Labels.achievementText("new_one", "New", "Desc"))
    }

    @Test
    fun presenceLabels() {
        assertEquals("Hors ligne", Labels.presence(null).text)
        assertEquals("Hors ligne", Labels.presence(Presence("u", "offline", "menu")).text)
        assertEquals("En partie", Labels.presence(Presence("u", "online", "in_match")).text)
        assertEquals("Sur mobile", Labels.presence(Presence("u", "online", "mobile")).text)
        assertEquals("Dans le menu", Labels.presence(Presence("u", "online", null)).text)
    }

    @Test
    fun ordinalsAndDuration() {
        assertEquals("1er", Format.ordinal(1))
        assertEquals("2e", Format.ordinal(2))
        assertEquals("7 min 05 s", Format.duration("2026-10-16T12:00:00Z", "2026-10-16T12:07:05Z"))
        assertEquals("0 min 00 s", Format.duration("2026-10-16T12:00:10Z", "2026-10-16T12:00:00Z"))
    }

    @Test
    fun parsesBackendTimestamps() {
        val expected = 1792152000000L // 2026-10-16T12:00:00Z
        assertEquals(expected, Format.parseIso("2026-10-16T12:00:00Z")?.time)
        assertEquals(expected, Format.parseIso("2026-10-16T14:00:00+02:00")?.time)
        assertEquals(expected + 123, Format.parseIso("2026-10-16T12:00:00.123456+00:00")?.time)
        assertEquals(expected, Format.parseIso("2026-10-16T12:00:00")?.time) // naive: UTC
        assertNull(Format.parseIso("hier"))
    }

    @Test
    fun formatsInFrench() {
        assertEquals("16/10/2026", Format.date("2026-10-16T12:00:00Z"))
        assertEquals("16/10 à 14:00", Format.dateTime("2026-10-16T12:00:00Z"))
        assertEquals("12 345", Format.number(12345))
        assertEquals("1,33", Format.ratio(1.3333))
    }

    @Test
    fun mergesMessagesInOrderWithoutDuplicates() {
        fun msg(id: String, at: String) = FriendMessage(id, "a", "b", "m$id", at)
        val merged = mergeMessages(
            listOf(msg("3", "2026-10-16T12:00:02Z"), msg("1", "2026-10-16T12:00:00Z")),
            listOf(msg("2", "2026-10-16T12:00:00Z"), msg("3", "2026-10-16T12:00:02Z")),
        )
        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
    }
}
