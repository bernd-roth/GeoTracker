package at.co.netconsulting.geotracker

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import at.co.netconsulting.geotracker.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StageGroupDatabaseTest {
    @Test fun `groups existing recordings and ungroups without deleting data`() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), FitnessTrackerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val userId = database.userDao().insertUser(User(firstName = "Test", lastName = "User", birthDate = "2000-01-01", weight = 70f, height = 180f))
            val groups = database.stageGroupDao()
            val id = groups.resolve(" Alpine Tour ")!!
            assertEquals(id, groups.resolve("alpine tour"))
            assertNull(groups.resolve(" "))
            val eventId = database.eventDao().insertEvent(Event(userId = userId, eventName = "Day one", eventDate = "2026-10-01", artOfSport = "Hiking", comment = "", stageGroupId = id, stageOrder = groups.nextOrder(id))).toInt()
            assertEquals(2, groups.nextOrder(id))
            assertEquals(eventId, groups.members(id).single().eventId)
            assertEquals("Alpine Tour", groups.observeStages().first().single().groupName)
            assertEquals(eventId, database.eventDao().searchRecordedEvents("alpine", 100).single().eventId)
            assertEquals(0L, groups.observeStages().first().single().durationMillis)
            groups.removeGroup(id)
            val event = database.eventDao().getEventById(eventId)!!
            assertNull(event.stageGroupId)
            assertNull(event.stageOrder)
            assertEquals("Day one", event.eventName)
            assertTrue(groups.observeGroups().first().isEmpty())
        } finally { database.close() }
    }

    @Test fun `migration preserves recordings and initially leaves them ungrouped`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .callback(object : SupportSQLiteOpenHelper.Callback(29) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE events (eventId INTEGER PRIMARY KEY, eventName TEXT NOT NULL)")
                    db.execSQL("INSERT INTO events VALUES (7, 'Existing recording')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            val db = helper.writableDatabase
            FitnessTrackerDatabase.MIGRATION_29_30.migrate(db)
            db.query("SELECT eventName, stageGroupId, stageOrder FROM events WHERE eventId = 7").use {
                assertTrue(it.moveToFirst())
                assertEquals("Existing recording", it.getString(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
            }
            db.execSQL("INSERT INTO stage_groups (name) VALUES ('Tour')")
            db.query("SELECT name FROM stage_groups").use { assertTrue(it.moveToFirst()); assertEquals("Tour", it.getString(0)) }
        } finally { helper.close() }
    }
}
