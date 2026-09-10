import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

public class DatabaseConnection {
    private static final String URL = "jdbc:sqlite:satbhai_bank.db";

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        createTableIfNotExists(conn);
        return conn;
    }

    private static void createTableIfNotExists(Connection conn) {
        String accountsSql = "CREATE TABLE IF NOT EXISTS accounts ("
                   + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                   + "name TEXT NOT NULL, "
                   + "phone TEXT NOT NULL, "
                   + "balance REAL DEFAULT 0"
                   + ");";

        String transactionsSql = "CREATE TABLE IF NOT EXISTS transactions ("
                   + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                   + "account_id INTEGER, "
                   + "type TEXT, "
                   + "amount REAL, "
                   + "description TEXT, "
                   + "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, "
                   + "FOREIGN KEY(account_id) REFERENCES accounts(id)"
                   + ");";

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(accountsSql);
            stmt.execute(transactionsSql);

            // Set auto-increment to start from 10001 for 5-digit account numbers
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sqlite_sequence WHERE name='accounts'");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("INSERT INTO sqlite_sequence (name, seq) VALUES ('accounts', 10000)");
            }
        } catch (SQLException e) {
            System.out.println("Error creating tables: " + e.getMessage());
        }
    }
}
