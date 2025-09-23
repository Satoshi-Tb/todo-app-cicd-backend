package com.example.taskapp.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void tasksTableExists_afterFlywayMigration() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, "TASKS", null)) {
                assertThat(rs.next()).as("tasks table should exist").isTrue();
            }
        }
    }
}

