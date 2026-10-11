package com.enanke.awardledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.enanke.awardledger.data.dao.AuditLogDao
import com.enanke.awardledger.data.dao.CandidateEventDao
import com.enanke.awardledger.data.dao.ChatMessageDao
import com.enanke.awardledger.data.dao.LedgerRecordDao
import com.enanke.awardledger.data.dao.MemberDao

@Database(
    entities = [
        MemberEntity::class,
        ChatMessageEntity::class,
        CandidateEventEntity::class,
        LedgerRecordEntity::class,
        AuditLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun candidateEventDao(): CandidateEventDao
    abstract fun ledgerRecordDao(): LedgerRecordDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val NAME = "award_ledger.db"

        /**
         * v1 -> v2：只移除“事迹发生日期/时间”列（`occurred_at`）。
         *
         * 安全约束：
         * - **不调用 `clearAllTables()`、不执行任何 `DELETE`**；成员、聊天消息、候选、
         *   台账记录、审计日志**一行不丢**，成员关联（`member_id`/`member_name`）原样保留。
         * - 名册由用户在应用内自行维护；初始化不种子化、不重置，已有数据库的成员一律不触碰。
         *
         * 实现方式：SQLite < 3.35 不支持 `ALTER TABLE ... DROP COLUMN`（Robolectric 的
         * SQLite 即如此），故采用等价且兼容性更好的**重建表 + 拷贝数据**：
         * 建同名结构的新表（去掉 `occurred_at`）→ 按列拷贝全部行 → 删旧表 → 改名 →
         * 重建索引。主键 `id` 显式拷贝，因此行 id 与全部外键/关联不变。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ---------------- candidate_events ----------------
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `candidate_events_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`chat_message_id` INTEGER NOT NULL, " +
                        "`idempotency_key` TEXT NOT NULL, " +
                        "`stated_name` TEXT, " +
                        "`member_id` INTEGER, " +
                        "`member_name` TEXT, " +
                        "`category` TEXT, " +
                        "`description` TEXT, " +
                        "`review_status` TEXT NOT NULL, " +
                        "`question` TEXT, " +
                        "FOREIGN KEY(`chat_message_id`) REFERENCES `chat_messages`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "INSERT INTO `candidate_events_new` (" +
                        "`id`, `chat_message_id`, `idempotency_key`, `stated_name`, `member_id`, " +
                        "`member_name`, `category`, `description`, `review_status`, `question`) " +
                        "SELECT `id`, `chat_message_id`, `idempotency_key`, `stated_name`, `member_id`, " +
                        "`member_name`, `category`, `description`, `review_status`, `question` " +
                        "FROM `candidate_events`",
                )
                db.execSQL("DROP TABLE `candidate_events`")
                db.execSQL("ALTER TABLE `candidate_events_new` RENAME TO `candidate_events`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_candidate_events_chat_message_id` " +
                        "ON `candidate_events` (`chat_message_id`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_candidate_events_idempotency_key` " +
                        "ON `candidate_events` (`idempotency_key`)",
                )

                // ---------------- ledger_records ----------------
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ledger_records_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`idempotency_key` TEXT NOT NULL, " +
                        "`member_id` INTEGER NOT NULL, " +
                        "`member_name` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`source_candidate_id` INTEGER NOT NULL, " +
                        "`source_message_id` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`supersedes_id` INTEGER, " +
                        "`superseded_by_id` INTEGER, " +
                        "`revision` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "INSERT INTO `ledger_records_new` (" +
                        "`id`, `idempotency_key`, `member_id`, `member_name`, `category`, `description`, " +
                        "`created_at`, `source_candidate_id`, `source_message_id`, `status`, " +
                        "`supersedes_id`, `superseded_by_id`, `revision`) " +
                        "SELECT `id`, `idempotency_key`, `member_id`, `member_name`, `category`, `description`, " +
                        "`created_at`, `source_candidate_id`, `source_message_id`, `status`, " +
                        "`supersedes_id`, `superseded_by_id`, `revision` FROM `ledger_records`",
                )
                db.execSQL("DROP TABLE `ledger_records`")
                db.execSQL("ALTER TABLE `ledger_records_new` RENAME TO `ledger_records`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ledger_records_member_name` " +
                        "ON `ledger_records` (`member_name`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_ledger_records_idempotency_key` " +
                        "ON `ledger_records` (`idempotency_key`)",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()

        /** 内存库，供单元/集成测试使用（Robolectric / instrumented）。 */
        fun buildInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
