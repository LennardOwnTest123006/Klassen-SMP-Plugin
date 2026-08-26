package de.klassensmp.database;

import java.sql.Connection;
import java.sql.SQLException;

/** Eine Datenbankabfrage mit Rueckgabewert. */
@FunctionalInterface
public interface SqlQuery<T> {

    T run(Connection connection) throws SQLException;
}
