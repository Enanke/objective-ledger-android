package com.enanke.awardledger.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 迁移测试：**不依赖 assets / schema JSON**。
 *
 * 原因：`MigrationTestHelper` 需要从测试 assets 读取
 * `com.enanke.awardledger.data.AppDatabase/1.json`，而 AGP 在 Robolectric 单元测试里
 * 不会把 `test` 源集的 assets 合并进去（实测 `Missing file: …AppDatabase/1.json`）。
 *
 * 因此这里按 v1 DDL 手工建 v1 库，然后：
 * 1. 直接调用真实迁移 [AppDatabase.MIGRATION_1_2]，断言「行数 / 内容 / 关联 / 索引 / 外键 / 列变化」；
 * 2. 用 Room 打开该 v1 库，让 Room 自己跑迁移并做 schema 校验（`Migration didn't properly handle` 会在此暴露）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseMigrationTest {

    private fun countRows(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    private fun tableColumns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info($table)").use { c ->
            val names = mutableSetOf<String>()
            val idx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) names.add(c.getString(idx))
            names
        }

    /** 索引名 -> 是否唯一。 */
    private fun indexUniqueFlags(db: SupportSQLiteDatabase, table: String): Map<String, Boolean> =
        db.query("PRAGMA index_list($table)").use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            val uniqueIdx = c.getColumnIndexOrThrow("unique")
            val out = mutableMapOf<String, Boolean>()
            while (c.moveToNext()) out[c.getString(nameIdx)] = c.getInt(uniqueIdx) == 1
            out
        }

    /** 某索引包含的列（按顺序）。 */
    private fun indexColumns(db: SupportSQLiteDatabase, index: String): List<String> =
        db.query("PRAGMA index_info($index)").use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            val out = mutableListOf<String>()
            while (c.moveToNext()) out.add(c.getString(nameIdx))
            out
        }

    /** 按 v1 schema（与 app/schemas/…/1.json 一致）新建一个 v1 库（user_version = 1）。 */
    private fun createV1Database(name: String = "migration-test.db"): SupportSQLiteDatabase {
        val context = RuntimeEnvironment.getApplication()
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                V1_SCHEMA.forEach { db.execSQL(it) }
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(name)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    /** 写入 v1 数据：2 个旧（虚构）成员 + 1 条消息 + 2 条候选 + 2 条台账 + 2 条审计。 */
    private fun insertV1Data(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO members (id, name, note, active) VALUES (1, '旧成员甲', NULL, 1)")
        db.execSQL("INSERT INTO members (id, name, note, active) VALUES (2, '旧成员乙', NULL, 1)")
        db.execSQL(
            "INSERT INTO chat_messages (id, client_message_id, raw_text, sent_at, process_state, created_at) " +
                "VALUES (1, 'c1', '旧话', 111, 'DONE', 111)",
        )
        db.execSQL(
            "INSERT INTO candidate_events (id, chat_message_id, idempotency_key, stated_name, member_id, member_name, category, description, occurred_at, review_status, question) " +
                "VALUES (1, 1, 'c1#0', '旧成员甲', 1, '旧成员甲', 'REWARD', '旧事迹A', 123, 'CONFIRMED', NULL)",
        )
        db.execSQL(
            "INSERT INTO candidate_events (id, chat_message_id, idempotency_key, stated_name, member_id, member_name, category, description, occurred_at, review_status, question) " +
                "VALUES (2, 1, 'c1#1', '旧成员乙', 2, '旧成员乙', 'PENALTY', '旧事迹B', 124, 'CONFIRMED', NULL)",
        )
        db.execSQL(
            "INSERT INTO ledger_records (id, idempotency_key, member_id, member_name, category, description, occurred_at, created_at, source_candidate_id, source_message_id, status, supersedes_id, superseded_by_id, revision) " +
                "VALUES (1, 'c1#0', 1, '旧成员甲', 'REWARD', '旧事迹A', 123, 111, 1, 1, 'CURRENT', NULL, NULL, 1)",
        )
        db.execSQL(
            "INSERT INTO ledger_records (id, idempotency_key, member_id, member_name, category, description, occurred_at, created_at, source_candidate_id, source_message_id, status, supersedes_id, superseded_by_id, revision) " +
                "VALUES (2, 'c1#1', 2, '旧成员乙', 'PENALTY', '旧事迹B', 124, 111, 2, 1, 'CURRENT', NULL, NULL, 1)",
        )
        db.execSQL(
            "INSERT INTO audit_log (id, timestamp, action, entity_type, entity_id, before_json, after_json) " +
                "VALUES (1, 111, 'CONFIRM', 'LEDGER_RECORD', 1, NULL, '{\"id\":1}')",
        )
        db.execSQL(
            "INSERT INTO audit_log (id, timestamp, action, entity_type, entity_id, before_json, after_json) " +
                "VALUES (2, 112, 'CORRECT', 'LEDGER_RECORD', 2, NULL, '{\"id\":2}')",
        )
    }

    // ---------- 1) 直接跑迁移，逐项核对 ----------

    @Test
    fun `migration 1 to 2 preserves all data and only drops occurred_at`() {
        val db = createV1Database()
        try {
            insertV1Data(db)
            AppDatabase.MIGRATION_1_2.migrate(db)

            // 每表行数不变：一行都不丢
            assertEquals(2, countRows(db, "members"))
            assertEquals(1, countRows(db, "chat_messages"))
            assertEquals(2, countRows(db, "candidate_events"))
            assertEquals(2, countRows(db, "ledger_records"))
            assertEquals(2, countRows(db, "audit_log"))

            // 仅 occurred_at 列被移除
            assertFalse(tableColumns(db, "candidate_events").contains("occurred_at"))
            assertFalse(tableColumns(db, "ledger_records").contains("occurred_at"))

            // 台账内容与成员关联不变（id 显式拷贝）
            db.query(
                "SELECT member_id, member_name, category, description, status, revision " +
                    "FROM ledger_records WHERE id = 1",
            ).use { c ->
                c.moveToFirst()
                assertEquals(1L, c.getLong(0))
                assertEquals("旧成员甲", c.getString(1))
                assertEquals("REWARD", c.getString(2))
                assertEquals("旧事迹A", c.getString(3))
                assertEquals("CURRENT", c.getString(4))
                assertEquals(1, c.getInt(5))
            }
            db.query("SELECT member_id, member_name, description FROM ledger_records WHERE id = 2").use { c ->
                c.moveToFirst()
                assertEquals(2L, c.getLong(0))
                assertEquals("旧成员乙", c.getString(1))
                assertEquals("旧事迹B", c.getString(2))
            }

            // 审计内容保留
            assertEquals(
                1,
                db.query("SELECT COUNT(*) FROM audit_log WHERE action = 'CONFIRM' AND entity_id = 1 AND after_json = '{\"id\":1}'")
                    .use { c -> c.moveToFirst(); c.getInt(0) },
            )
            assertEquals(
                1,
                db.query("SELECT COUNT(*) FROM audit_log WHERE action = 'CORRECT' AND entity_id = 2 AND after_json = '{\"id\":2}'")
                    .use { c -> c.moveToFirst(); c.getInt(0) },
            )

            // 索引完整重建（名称 + 唯一性 + 列）
            val ceIdx = indexUniqueFlags(db, "candidate_events")
            assertTrue(ceIdx.containsKey("index_candidate_events_chat_message_id"))
            assertEquals(false, ceIdx["index_candidate_events_chat_message_id"])
            assertTrue(ceIdx.containsKey("index_candidate_events_idempotency_key"))
            assertEquals(true, ceIdx["index_candidate_events_idempotency_key"])
            assertEquals(listOf("chat_message_id"), indexColumns(db, "index_candidate_events_chat_message_id"))
            assertEquals(listOf("idempotency_key"), indexColumns(db, "index_candidate_events_idempotency_key"))

            val lrIdx = indexUniqueFlags(db, "ledger_records")
            assertTrue(lrIdx.containsKey("index_ledger_records_member_name"))
            assertEquals(false, lrIdx["index_ledger_records_member_name"])
            assertTrue(lrIdx.containsKey("index_ledger_records_idempotency_key"))
            assertEquals(true, lrIdx["index_ledger_records_idempotency_key"])
            assertEquals(listOf("member_name"), indexColumns(db, "index_ledger_records_member_name"))
            assertEquals(listOf("idempotency_key"), indexColumns(db, "index_ledger_records_idempotency_key"))

            // 外键保留：candidate_events.chat_message_id -> chat_messages.id ON DELETE CASCADE
            db.query("PRAGMA foreign_key_list(candidate_events)").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals("chat_messages", c.getString(c.getColumnIndexOrThrow("table")))
                assertEquals("chat_message_id", c.getString(c.getColumnIndexOrThrow("from")))
                assertEquals("id", c.getString(c.getColumnIndexOrThrow("to")))
                assertEquals("CASCADE", c.getString(c.getColumnIndexOrThrow("on_delete")))
            }

            // 迁移本身不改成员数据（名册由用户在应用内自行维护，初始化不种子化、不重置）
            db.query("SELECT name, active FROM members WHERE id = 1").use { c ->
                c.moveToFirst()
                assertEquals("旧成员甲", c.getString(0))
                assertEquals(1, c.getInt(1))
            }
        } finally {
            db.close()
        }
    }

    // ---------- 2) 由 Room 亲自迁移并做 schema 校验 ----------

    @Test
    fun `room migrates a v1 database and validates the schema`() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "room-migration-test.db"
        context.deleteDatabase(dbName)

        createV1Database(dbName).use { v1 -> insertV1Data(v1) }

        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                // 首次访问触发打开：Room 跑 MIGRATION_1_2 并校验 schema；
                // 若重建表结构与 v2 不一致，这里会抛 "Migration didn't properly handle"。
                val records = room.ledgerRecordDao().all()
                assertEquals(2, records.size)
                val first = records.first { it.id == 1L }
                assertEquals("旧成员甲", first.memberName)
                assertEquals(1L, first.memberId)
                assertEquals("旧事迹A", first.description)

                assertEquals(2, room.memberDao().allIncludingInactive().size)
                assertEquals(1, room.chatMessageDao().byStates(listOf("DONE")).size)
                assertEquals(2, room.auditLogDao().all().size)
            }
        } finally {
            room.close()
            context.deleteDatabase(dbName)
        }
    }

    private companion object {
        /** v1 DDL，逐条取自 app/schemas/com.enanke.awardledger.data.AppDatabase/1.json。 */
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `members` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `note` TEXT, `active` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_members_name` ON `members` (`name`)",
            "CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `client_message_id` TEXT NOT NULL, `raw_text` TEXT NOT NULL, `sent_at` INTEGER NOT NULL, `process_state` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_chat_messages_client_message_id` ON `chat_messages` (`client_message_id`)",
            "CREATE TABLE IF NOT EXISTS `candidate_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chat_message_id` INTEGER NOT NULL, `idempotency_key` TEXT NOT NULL, `stated_name` TEXT, `member_id` INTEGER, `member_name` TEXT, `category` TEXT, `description` TEXT, `occurred_at` INTEGER, `review_status` TEXT NOT NULL, `question` TEXT, FOREIGN KEY(`chat_message_id`) REFERENCES `chat_messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_candidate_events_chat_message_id` ON `candidate_events` (`chat_message_id`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_candidate_events_idempotency_key` ON `candidate_events` (`idempotency_key`)",
            "CREATE TABLE IF NOT EXISTS `ledger_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `idempotency_key` TEXT NOT NULL, `member_id` INTEGER NOT NULL, `member_name` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `occurred_at` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `source_candidate_id` INTEGER NOT NULL, `source_message_id` INTEGER NOT NULL, `status` TEXT NOT NULL, `supersedes_id` INTEGER, `superseded_by_id` INTEGER, `revision` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_ledger_records_member_name` ON `ledger_records` (`member_name`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_records_idempotency_key` ON `ledger_records` (`idempotency_key`)",
            "CREATE TABLE IF NOT EXISTS `audit_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `action` TEXT NOT NULL, `entity_type` TEXT NOT NULL, `entity_id` INTEGER NOT NULL, `before_json` TEXT, `after_json` TEXT)",
        )
    }
}
