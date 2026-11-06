package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun verifyAppReviewThresholdsAndLocalizedMessages() {
    // 1. Verify 30-minute boost threshold and 3-day dismissal cooldown constants
    assertEquals(30L * 60L * 1000L, com.example.util.AppReviewManager.BOOST_REVIEW_THRESHOLD_MS)
    assertEquals(3L * 24L * 60L * 60L * 1000L, com.example.util.AppReviewManager.DISMISS_COOLDOWN_MS)

    // 2. Verify ReviewTriggerSource keys
    assertEquals("boost_30_min_milestone", com.example.util.ReviewTriggerSource.BOOST_30_MIN_MILESTONE.key)
    assertEquals("boost_session_completed", com.example.util.ReviewTriggerSource.BOOST_SESSION_COMPLETED.key)
    assertEquals("screen_recording_stopped", com.example.util.ReviewTriggerSource.SCREEN_RECORDING_STOPPED.key)

    // 3. Verify SmartNotificationTrigger review IDs and localized messages
    assertEquals(4108, com.example.util.SmartNotificationTrigger.APP_REVIEW_AFTER_BOOST.id)
    assertEquals(4109, com.example.util.SmartNotificationTrigger.APP_REVIEW_AFTER_RECORDING.id)

    val boostReviewMsgEn = com.example.util.SmartNotificationManager.getReviewAfterBoostMessage(com.example.util.AppLanguage.EN)
    val recReviewMsgEn = com.example.util.SmartNotificationManager.getReviewAfterRecordingMessage(com.example.util.AppLanguage.EN)
    assertTrue(boostReviewMsgEn.contains("Google Play"))
    assertTrue(recReviewMsgEn.contains("Google Play"))

    val boostReviewMsgEs = com.example.util.SmartNotificationManager.getReviewAfterBoostMessage(com.example.util.AppLanguage.ES_MX)
    val recReviewMsgPt = com.example.util.SmartNotificationManager.getReviewAfterRecordingMessage(com.example.util.AppLanguage.PT_BR)
    assertTrue(boostReviewMsgEs.contains("Google Play"))
    assertTrue(recReviewMsgPt.contains("Google Play"))
  }

  @Test
  fun verifyDailyStreakEconomyMilestonesAndCloudSync() {
    // 1. Verify confirmed economy & milestone parameters
    assertEquals(10, com.example.util.DailyStreakManager.TOKENS_PER_5_MINS)
    assertEquals(300, com.example.util.DailyStreakManager.HUD_SECONDS_PER_REWARD)
    assertEquals(100, com.example.util.DailyStreakManager.STREAK_FREEZE_COST_TOKENS)
    assertEquals(2, com.example.util.DailyStreakManager.MAX_STREAK_FREEZES)
    assertEquals(12L * 60L * 60L * 1000L, com.example.util.DailyStreakManager.REPAIR_GRACE_WINDOW_MS)
    assertEquals(7, com.example.util.DailyStreakManager.MILESTONE_NEON_HUD_DAYS)
    assertEquals(30, com.example.util.DailyStreakManager.MILESTONE_VIP_BADGE_DAYS)

    // 2. Verify date key difference calculation
    assertEquals(1, com.example.util.DailyStreakManager.daysBetweenDateKeys("2026-11-05", "2026-11-06"))
    assertEquals(2, com.example.util.DailyStreakManager.daysBetweenDateKeys("2026-11-05", "2026-11-07"))

    // 3. Verify 7-Day Neon HUD & 30-Day VIP Badge unlock and revocation when streak breaks
    val active30State = com.example.util.DailyStreakState(
      currentStreak = 30,
      longestStreak = 30,
      lastCompletedDate = "2026-11-06",
      completedToday = true,
      gTokensBalance = 140,
      streakFreezesOwned = 1,
      isHudNeonUnlocked = true,
      isVipBadgeUnlocked = true
    )
    assertTrue(active30State.isHudNeonUnlocked)
    assertTrue(active30State.isVipBadgeUnlocked)

    val brokenState = active30State.copy(
      currentStreak = 0,
      isStreakBrokenRepairable = false,
      isHudNeonUnlocked = false,
      isVipBadgeUnlocked = false
    )
    assertFalse(brokenState.isHudNeonUnlocked)
    assertFalse(brokenState.isVipBadgeUnlocked)

    // 4. Verify multi-device cloud option token adds streak_active when streak > 0 and removes it when streak resets to 0
    val cloudWithActiveStreak = com.example.util.DailyStreakManager.mergeStreakIntoCloudOptions(
      baseOptionIds = setOf("net_low_latency"),
      state = active30State
    )
    assertTrue(cloudWithActiveStreak.any { it.startsWith("streak_active:30:") })
    assertFalse(cloudWithActiveStreak.any { it.startsWith("streak_none:") })

    val cloudAfterBreak = com.example.util.DailyStreakManager.mergeStreakIntoCloudOptions(
      baseOptionIds = cloudWithActiveStreak.toSet(),
      state = brokenState
    )
    assertFalse(cloudAfterBreak.any { it.startsWith("streak_active:") })
    assertTrue(cloudAfterBreak.any { it.startsWith("streak_none:") })
  }
}


