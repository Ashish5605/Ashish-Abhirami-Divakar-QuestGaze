
package questgaze.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {

    private static final String URL =
            "jdbc:mysql://localhost:3306/questgaze";

    private static final String USER = "root";

    // Replace with your actual MySQL root password
    private static final String PASSWORD = "root";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
