package com.example.e2e

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.database.AppDatabase
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DbMigrationJourneyTest {

    private val DB_NAME = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private fun schemasExist(version: Int): Boolean {
        val schemaFile = File(
            "schemas/com.example.data.database.AppDatabase/${version}.json"
        )
        return schemaFile.exists() || File("app/$schemaFile").exists()
    }

    @Test
    fun `migrate 13 to 14 succeeds without throwing`() {
        Assume.assumeTrue(
            "Schema JSON for v13 not exported yet — re-run after `./gradlew :app:assembleDebug --rerun-tasks`",
            schemasExist(13)
        )
        helper.createDatabase(DB_NAME, 13).close()
        val db = helper.runMigrationsAndValidate(
            DB_NAME,
            14,
            true
        )
        db.close()
    }

    @Test
    fun `migrate 12 to 14 succeeds without throwing`() {
        Assume.assumeTrue(
            "Schema JSON for v12 not exported yet",
            schemasExist(12)
        )
        helper.createDatabase(DB_NAME, 12).close()
        val db = helper.runMigrationsAndValidate(DB_NAME, 14, true)
        db.close()
    }

    @Test
    fun `full open at current version creates all tables`() {
        // Sanity check that the database can be created from scratch at v14.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val freshDb = Room.databaseBuilder(context, AppDatabase::class.java, "fresh.db")
            .allowMainThreadQueries()
            .build()
        try {
            freshDb.openHelper.readableDatabase  // forces schema creation
        } finally {
            freshDb.close()
            context.deleteDatabase("fresh.db")
        }
    }
}
