package com.sohva.tv.core.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/** Builds the main database with the settings plan/03 §4.7 and plan/04 §15.1 require. */
object DatabaseFactory {
    /** Three reader threads for screen queries, one writer for small user writes (plan/03 §4.7). */
    private const val READ_THREADS = 3

    fun create(context: Context, name: String = SohvaDatabase.FILE_NAME): SohvaDatabase =
        Room.databaseBuilder(context, SohvaDatabase::class.java, name)
            // Explicit WAL: Room's automatic mode falls back to a rollback journal on devices that
            // report isLowRamDevice (some 1 GB sticks), and then screen reads wait for imports.
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .setQueryExecutor(executor("SohvaDbRead", READ_THREADS))
            .setTransactionExecutor(executor("SohvaDbWrite", 1))
            .addCallback(PragmaCallback)
            .addCallback(SearchIndex.Callback)
            .build()

    private fun executor(name: String, threads: Int): ExecutorService {
        val count = AtomicInteger()
        val factory = ThreadFactory { runnable -> Thread(runnable, "$name-${count.incrementAndGet()}") }
        return Executors.newFixedThreadPool(threads, factory)
    }

    /** WAL makes NORMAL durable enough and saves an fsync per transaction (plan/04 §15.1). */
    private object PragmaCallback : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.query("PRAGMA synchronous = NORMAL").close()
        }
    }
}
