import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class BankApiServer {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/create", new CreateAccountHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/transaction", new TransactionHandler());
        server.createContext("/api/transfer", new TransferHandler());
        server.createContext("/api/statement", new StatementHandler());
        server.createContext("/api/verify", new VerifyHandler());
        server.createContext("/api/admin/data", new AdminDataHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Satbhai Pvt Bank Server started on http://localhost:8080");
    }

    // --- Helpers ---
    private static Map<String, String> parseFormData(String formData) {
        Map<String, String> map = new HashMap<>();
        if (formData == null || formData.isEmpty()) return map;
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                map.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.getResponseBody().close();
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // --- Serve HTML ---
    static class StaticFileHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            File file = new File("index.html");
            if (file.exists()) {
                byte[] bytes = Files.readAllBytes(file.toPath());
                ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.getResponseBody().close();
            } else {
                String r = "index.html not found.";
                ex.sendResponseHeaders(404, r.length());
                ex.getResponseBody().write(r.getBytes());
                ex.getResponseBody().close();
            }
        }
    }

    // --- Create Account ---
    static class CreateAccountHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) return;
            Map<String, String> p = parseFormData(readBody(ex));
            String name = p.get("name");
            String phone = p.get("phone");

            if (name == null || phone == null || name.isEmpty() || phone.isEmpty()) {
                sendJson(ex, 400, "{\"success\":false,\"message\":\"Name and Phone are required.\"}");
                return;
            }

            String q = "INSERT INTO accounts (name, phone, balance) VALUES (?, ?, 0)";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(q, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, phone);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        sendJson(ex, 200, "{\"success\":true,\"id\":" + rs.getLong(1) + "}");
                    }
                }
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"success\":false,\"message\":\"Database error.\"}");
            }
        }
    }

    // --- Login ---
    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) return;
            Map<String, String> p = parseFormData(readBody(ex));
            String phone = p.getOrDefault("phone", "");

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM accounts WHERE phone=?")) {
                ps.setString(1, phone);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    sendJson(ex, 200, String.format(
                        "{\"success\":true,\"id\":%d,\"name\":\"%s\",\"phone\":\"%s\",\"balance\":%.2f}",
                        rs.getInt("id"), esc(rs.getString("name")), esc(rs.getString("phone")), rs.getDouble("balance")));
                } else {
                    sendJson(ex, 404, "{\"success\":false,\"message\":\"Phone number not registered.\"}");
                }
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"success\":false,\"message\":\"Database error.\"}");
            }
        }
    }

    // --- Deposit / Withdraw ---
    static class TransactionHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) return;
            Map<String, String> p = parseFormData(readBody(ex));
            int id = Integer.parseInt(p.getOrDefault("id", "0"));
            double amount = Double.parseDouble(p.getOrDefault("amount", "0"));
            String type = p.get("type");

            if (amount <= 0) {
                sendJson(ex, 400, "{\"success\":false,\"message\":\"Amount must be greater than zero.\"}");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                conn.setAutoCommit(false);

                if ("WITHDRAW".equals(type)) {
                    try (PreparedStatement cs = conn.prepareStatement("SELECT balance FROM accounts WHERE id=?")) {
                        cs.setInt(1, id);
                        ResultSet rs = cs.executeQuery();
                        if (rs.next()) {
                            if (rs.getDouble("balance") < amount) {
                                conn.rollback();
                                sendJson(ex, 400, "{\"success\":false,\"message\":\"Insufficient balance!\"}");
                                return;
                            }
                        } else {
                            conn.rollback();
                            sendJson(ex, 404, "{\"success\":false,\"message\":\"Account not found.\"}");
                            return;
                        }
                    }
                }

                String uq = "WITHDRAW".equals(type)
                    ? "UPDATE accounts SET balance=balance-? WHERE id=?"
                    : "UPDATE accounts SET balance=balance+? WHERE id=?";
                try (PreparedStatement us = conn.prepareStatement(uq)) {
                    us.setDouble(1, amount);
                    us.setInt(2, id);
                    int rows = us.executeUpdate();
                    if (rows == 0) { conn.rollback(); sendJson(ex, 404, "{\"success\":false,\"message\":\"Account not found.\"}"); return; }
                }

                String desc = "DEPOSIT".equals(type) ? "Cash Deposit" : "Cash Withdrawal";
                try (PreparedStatement ls = conn.prepareStatement("INSERT INTO transactions(account_id,type,amount,description) VALUES(?,?,?,?)")) {
                    ls.setInt(1, id); ls.setString(2, type); ls.setDouble(3, amount); ls.setString(4, desc);
                    ls.executeUpdate();
                }
                conn.commit();
                sendJson(ex, 200, "{\"success\":true,\"message\":\"Transaction successful!\"}");
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"success\":false,\"message\":\"Database error.\"}");
            }
        }
    }

    // --- Fund Transfer (UPI Style) ---
    static class TransferHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) return;
            Map<String, String> p = parseFormData(readBody(ex));
            int fromId = Integer.parseInt(p.getOrDefault("from", "0"));
            String toPhoneOrId = p.getOrDefault("to", "");
            double amount = Double.parseDouble(p.getOrDefault("amount", "0"));

            if (toPhoneOrId.isEmpty()) {
                sendJson(ex, 400, "{\"success\":false,\"message\":\"Receiver details required.\"}");
                return;
            }
            if (amount <= 0) {
                sendJson(ex, 400, "{\"success\":false,\"message\":\"Amount must be greater than zero.\"}");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                conn.setAutoCommit(false);

                // Check sender
                double senderBal = -1;
                String senderPhone = "";
                try (PreparedStatement cs = conn.prepareStatement("SELECT balance, phone FROM accounts WHERE id=?")) {
                    cs.setInt(1, fromId);
                    ResultSet rs = cs.executeQuery();
                    if (rs.next()) {
                        senderBal = rs.getDouble("balance");
                        senderPhone = rs.getString("phone");
                    }
                    else { conn.rollback(); sendJson(ex, 404, "{\"success\":false,\"message\":\"Your account not found.\"}"); return; }
                }

                if (toPhoneOrId.equals(senderPhone) || toPhoneOrId.equals(String.valueOf(fromId))) {
                    conn.rollback();
                    sendJson(ex, 400, "{\"success\":false,\"message\":\"Cannot transfer to yourself.\"}");
                    return;
                }

                if (senderBal < amount) {
                    conn.rollback();
                    sendJson(ex, 400, "{\"success\":false,\"message\":\"Insufficient balance!\"}");
                    return;
                }

                // Check receiver by Phone or ID
                String receiverName = null;
                int toId = 0;
                String query = "SELECT id, name FROM accounts WHERE phone=? OR id=?";
                try (PreparedStatement cs = conn.prepareStatement(query)) {
                    cs.setString(1, toPhoneOrId);
                    try { cs.setInt(2, Integer.parseInt(toPhoneOrId)); } catch(Exception e) { cs.setInt(2, 0); }
                    
                    ResultSet rs = cs.executeQuery();
                    if (rs.next()) {
                        toId = rs.getInt("id");
                        receiverName = rs.getString("name");
                    }
                    else { conn.rollback(); sendJson(ex, 404, "{\"success\":false,\"message\":\"Receiver not found on Satbhai UPI.\"}"); return; }
                }

                // Update balances
                try (PreparedStatement us = conn.prepareStatement("UPDATE accounts SET balance=balance-? WHERE id=?")) {
                    us.setDouble(1, amount); us.setInt(2, fromId); us.executeUpdate();
                }
                try (PreparedStatement us = conn.prepareStatement("UPDATE accounts SET balance=balance+? WHERE id=?")) {
                    us.setDouble(1, amount); us.setInt(2, toId); us.executeUpdate();
                }

                // Ledger entries
                try (PreparedStatement ls = conn.prepareStatement("INSERT INTO transactions(account_id,type,amount,description) VALUES(?,?,?,?)")) {
                    ls.setInt(1, fromId); ls.setString(2, "TRANSFER_OUT"); ls.setDouble(3, amount);
                    ls.setString(4, "Paid to " + esc(receiverName));
                    ls.executeUpdate();
                }
                String senderName = "";
                try (PreparedStatement sn = conn.prepareStatement("SELECT name FROM accounts WHERE id=?")) {
                    sn.setInt(1, fromId); ResultSet rs = sn.executeQuery(); if (rs.next()) senderName = rs.getString("name");
                }
                try (PreparedStatement ls = conn.prepareStatement("INSERT INTO transactions(account_id,type,amount,description) VALUES(?,?,?,?)")) {
                    ls.setInt(1, toId); ls.setString(2, "TRANSFER_IN"); ls.setDouble(3, amount);
                    ls.setString(4, "Received from " + esc(senderName));
                    ls.executeUpdate();
                }

                conn.commit();
                sendJson(ex, 200, "{\"success\":true,\"message\":\"Successfully sent Rs. " + String.format("%.2f", amount) + " to " + esc(receiverName) + "\"}");
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"success\":false,\"message\":\"Database error.\"}");
            }
        }
    }

    // --- Mini Statement ---
    static class StatementHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equals(ex.getRequestMethod())) return;
            Map<String, String> p = parseFormData(ex.getRequestURI().getQuery());
            int id = Integer.parseInt(p.getOrDefault("id", "0"));

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM transactions WHERE account_id=? ORDER BY timestamp DESC LIMIT 30")) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append(String.format("{\"type\":\"%s\",\"amount\":%.2f,\"description\":\"%s\",\"timestamp\":\"%s\"}",
                        rs.getString("type"), rs.getDouble("amount"), esc(rs.getString("description")), rs.getString("timestamp")));
                    first = false;
                }
                sb.append("]");
                sendJson(ex, 200, "{\"success\":true,\"transactions\":" + sb + "}");
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"success\":false,\"message\":\"Database error.\"}");
            }
        }
    }

    // --- Verify UPI / Phone ---
    static class VerifyHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) return;
            Map<String, String> p = parseFormData(readBody(ex));
            String phone = p.getOrDefault("phone", "");

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT name FROM accounts WHERE phone=? OR id=?")) {
                ps.setString(1, phone);
                try { ps.setInt(2, Integer.parseInt(phone)); } catch(Exception e) { ps.setInt(2, 0); }
                
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    sendJson(ex, 200, "{\"success\":true,\"name\":\"" + esc(rs.getString("name")) + "\"}");
                } else {
                    sendJson(ex, 404, "{\"success\":false,\"message\":\"UPI ID not found.\"}");
                }
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"success\":false,\"message\":\"Database error.\"}");
            }
        }
    }

    // --- Admin Data ---
    static class AdminDataHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equals(ex.getRequestMethod())) return;
            try (Connection conn = DatabaseConnection.getConnection()) {
                double vault = 0;
                int users = 0;
                StringBuilder accs = new StringBuilder("[");
                try (Statement s = conn.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM accounts ORDER BY id DESC")) {
                    boolean first = true;
                    while(rs.next()) {
                        if(!first) accs.append(",");
                        vault += rs.getDouble("balance");
                        users++;
                        accs.append(String.format("{\"id\":%d,\"name\":\"%s\",\"phone\":\"%s\",\"balance\":%.2f}",
                            rs.getInt("id"), esc(rs.getString("name")), esc(rs.getString("phone")), rs.getDouble("balance")));
                        first = false;
                    }
                }
                accs.append("]");
                sendJson(ex, 200, "{\"success\":true,\"vault\":" + vault + ",\"users\":" + users + ",\"accounts\":" + accs + "}");
            } catch (SQLException e) {
                sendJson(ex, 500, "{\"success\":false}");
            }
        }
    }
}
