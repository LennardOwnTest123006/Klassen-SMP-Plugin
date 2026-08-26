package de.klassensmp.database;

import java.sql.Connection;
import java.sql.SQLException;

/** Eine Datenbankoperation, die auf dem Datenbank-Thread ausgefuehrt wird. */
@FunctionalInterface
public interface SqlTask {

    void run(Connection connection) throws SQLException;
}
