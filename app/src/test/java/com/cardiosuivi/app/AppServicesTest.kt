package com.cardiosuivi.app

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.cardiosuivi.app.ai.AiClient
import com.cardiosuivi.app.ai.AiService
import com.cardiosuivi.app.data.AiProvider
import com.cardiosuivi.app.data.SettingsRepository
import com.cardiosuivi.app.ocr.ImagePreprocessor
import com.cardiosuivi.app.reminders.ReminderScheduler
import com.cardiosuivi.app.security.SecretStore
import com.cardiosuivi.domain.ai.AiContext
import com.cardiosuivi.domain.ai.AiSafetyGuard
import com.cardiosuivi.domain.model.ReminderConfig
import com.cardiosuivi.domain.model.SessionPeriod
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AppServicesTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx, Configuration.Builder().build())
    }

    @Test
    fun schedulerSetsNextMorningAndEveningAlarms() {
        val zone = ZoneId.of("Europe/Paris")
        val now = ZonedDateTime.of(LocalDate.of(2026, 11, 14), LocalTime.of(10, 0), zone) // samedi
        val slots = ReminderScheduler(ctx).rescheduleAll(ReminderConfig(), now)
        assertEquals(2, slots.size)
        assertEquals(LocalDate.of(2026, 11, 18), slots[0].date)
        assertEquals(SessionPeriod.MORNING, slots[0].period)
        assertEquals(SessionPeriod.EVENING, slots[1].period)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val scheduled = shadowOf(am).scheduledAlarms
        assertEquals(2, scheduled.size)
        val times = scheduled.map { it.triggerAtTime }.sorted()
        assertEquals(slots[0].at(zone).toInstant().toEpochMilli(), times[0])
        assertEquals(slots[1].at(zone).toInstant().toEpochMilli(), times[1])
    }

    @Test
    fun disabledRemindersScheduleNothing() {
        val slots = ReminderScheduler(ctx).rescheduleAll(ReminderConfig(enabled = false))
        assertTrue(slots.isEmpty())
        assertTrue(shadowOf(ctx.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }

    @Test
    fun prescriptionQuestionNeverReachesProvider() = runTest {
        var called = false
        val fake = object : AiClient {
            override suspend fun complete(system: String, user: String, apiKey: String, model: String): String { called = true; return "x" }
        }
        val settings = SettingsRepository(ctx)
        // SecretStore requiert le Keystore Android, indisponible sous Robolectric : le garde-fou doit agir avant.
        val service = AiService(settings, SecretStore(ctx), fake, fake)
        settings.setAiProvider(AiProvider.ANTHROPIC)
        settings.setConsent(AiProvider.ANTHROPIC, true)
        val ctxData = AiContext("2026-11-14", "m", null, null, null, null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null)
        val a = service.ask("Dois-je augmenter mon traitement ce soir ?", ctxData)
        assertFalse(called)
        assertFalse(a.sentToProvider)
        assertEquals(AiSafetyGuard.REFUSAL, a.text)
    }

    @Test
    fun otsuThresholdSeparatesTwoClasses() {
        val hist = IntArray(256).also { it[30] = 500; it[220] = 500 }
        val t = ImagePreprocessor.otsu(hist, 1000)
        assertTrue("seuil $t", t in 30 until 220)
    }

    @Test
    fun binarizeProducesBlackAndWhiteOnly() {
        val bmp = android.graphics.Bitmap.createBitmap(4, 1, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, android.graphics.Color.rgb(10, 10, 10)); bmp.setPixel(1, 0, android.graphics.Color.rgb(20, 20, 20))
        bmp.setPixel(2, 0, android.graphics.Color.rgb(230, 230, 230)); bmp.setPixel(3, 0, android.graphics.Color.rgb(240, 240, 240))
        val out = ImagePreprocessor.binarize(bmp)
        assertEquals(android.graphics.Color.BLACK, out.getPixel(0, 0))
        assertEquals(android.graphics.Color.WHITE, out.getPixel(3, 0))
        val inv = ImagePreprocessor.binarize(bmp, invert = true)
        assertEquals(android.graphics.Color.WHITE, inv.getPixel(0, 0))
    }
}

class AppLockTest {
    @org.junit.Test
    fun shortExcursionDoesNotLockButLongAbsenceDoes() {
        var now = 1_000_000L
        val lock = com.cardiosuivi.app.security.AppLock { now }
        org.junit.Assert.assertTrue(lock.locked.value) // verrouillée au démarrage
        lock.unlock()
        lock.onBackground(); now += 20_000; lock.onForeground()
        org.junit.Assert.assertFalse(lock.locked.value) // sélecteur de photo, partage…
        lock.onBackground(); now += 61_000; lock.onForeground()
        org.junit.Assert.assertTrue(lock.locked.value)
    }
}
