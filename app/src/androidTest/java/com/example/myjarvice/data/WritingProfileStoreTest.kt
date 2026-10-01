package com.example.myjarvice.data

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Isolated in the test APK's package; never modifies the user's Jarvis profile. */
class WritingProfileStoreTest {
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context
    private val store get() = WritingProfileStore(testContext)

    @Before fun prepare() { store.reset() }
    @After fun cleanUp() { store.reset() }

    @Test fun savedProfileSurvivesReopeningAndCanBeReset() {
        val profile = WritingProfile(true, DraftTone.PROFESSIONAL, DraftLength.SHORT, false, true,
            "  Cheers, Alex  ", "kindly")
        store.save(profile)
        val reopened = WritingProfileStore(testContext)
        assertEquals(profile.normalized(), reopened.load())
        reopened.reset()
        assertEquals(WritingProfile(), WritingProfileStore(testContext).load())
    }
}
