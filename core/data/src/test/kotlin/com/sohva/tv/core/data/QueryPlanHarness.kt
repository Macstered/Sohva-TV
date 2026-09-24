package com.sohva.tv.core.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * JVM query-plan tests on the exported Room schema (plan/07 §4.3.4): every hot query is a
 * constant, and its plan must not scan a large table or build a temporary B-tree, with and
 * without statistics. Uses sqlite-jdbc, so it runs without a device.
 */
class QueryPlanHarness private constructor(private val connection: Connection) : AutoCloseable {
    fun plan(sql: String): List<String> {
        val bound = sql.replace(Regex(""":\w+"""), "?")
        connection.prepareStatement("EXPLAIN QUERY PLAN $bound").use { statement ->
            repeat(bound.count { it == '?' }) { statement.setObject(it + 1, null) }
            statement.executeQuery().use { rows ->
                val details = ArrayList<String>()
                while (rows.next()) details += rows.getString("detail")
                return details
            }
        }
    }

    fun analyze() {
        connection.createStatement().use { it.execute("ANALYZE") }
    }

    override fun close() = connection.close()

    companion object {
        private val tableName = Regex(""""tableName"\s*:\s*"(\w+)"""")
        private val createSql = Regex(""""createSql"\s*:\s*"((?:[^"\\]|\\.)*)"""")

        /** Opens an in-memory database with the tables and indexes of schema [version]. */
        fun open(version: Int): QueryPlanHarness {
            val file = File("schemas/com.sohva.tv.core.data.database.SohvaDatabase/$version.json")
            require(file.isFile) { "No exported schema at ${file.absolutePath}; build the module first" }
            val json = file.readText()
            val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
            connection.createStatement().use { statement ->
                // An entity's table and index statements name their table as a TABLE_NAME placeholder.
                val tables = tableName.findAll(json).toList()
                tables.forEachIndexed { i, table ->
                    val end = tables.getOrNull(i + 1)?.range?.first ?: json.length
                    createSql.findAll(json.substring(table.range.first, end)).forEach { match ->
                        val sql = match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
                        statement.execute(sql.replace("\${TABLE_NAME}", table.groupValues[1]))
                    }
                }
            }
            return QueryPlanHarness(connection)
        }
    }
}
