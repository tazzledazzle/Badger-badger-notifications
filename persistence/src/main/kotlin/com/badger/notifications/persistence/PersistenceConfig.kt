package com.badger.notifications.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource as JdbcDataSource

data class PersistenceConfig(
    val jdbcUrl: String,
    val username: String = "badger",
    val password: String = "badger",
    val maximumPoolSize: Int = 10,
) {
    fun buildDataSource(): JdbcDataSource {
        val hc =
            HikariConfig().apply {
                jdbcUrl = this@PersistenceConfig.jdbcUrl
                username = this@PersistenceConfig.username
                password = this@PersistenceConfig.password
                maximumPoolSize = this@PersistenceConfig.maximumPoolSize
                poolName = "badger-pool"
            }
        return HikariDataSource(hc)
    }

    companion object {
        fun migrate(dataSource: JdbcDataSource) {
            Flyway
                .configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }
    }
}
