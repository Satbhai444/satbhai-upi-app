# Satbhai UPI App 📱💸

A complete, mobile-first UPI simulation application built entirely from scratch without heavy frameworks. This project demonstrates a full-stack architecture using a lightweight Java HTTP Server backend and a modern Vanilla JS/CSS frontend.

## ✨ Features
* **True UPI Flow:** Register with a 10-digit mobile number to automatically generate a UPI ID (`phone@satbhai`).
* **Scan & Pay:** Generate unique receiving QR codes and scan via the device camera (or use the built-in simulator) to initiate instant transfers.
* **Double-Entry Ledger:** All transactions are securely recorded in an SQLite database ensuring data integrity.
* **Live Synchronization:** Multi-tab support updates your balance instantly without needing a page refresh.
* **Bank Admin Portal:** Access `/api/admin/data` (Password: `admin123`) to view total bank vault balances and user ledgers.
* **Mobile-First SPA:** Sleek, app-like UI with slide-up numeric PIN pads, processing animations, and toast notifications.

## 🛠️ Tech Stack
* **Backend:** Pure Java (`com.sun.net.httpserver.HttpServer`)
* **Database:** SQLite (`sqlite-jdbc`)
* **Frontend:** HTML5, CSS3, Vanilla JavaScript
* **Libraries:** Chart.js (Analytics), QRCode.js, HTML5-QRCode

## 🚀 How to Run Locally
1. Ensure you have the Java Development Kit (JDK) installed.
2. Clone this repository to your local machine.
3. Open your terminal in the project folder and compile the code:
   ```bash
   javac -encoding utf8 -cp ".;sqlite-jdbc.jar" *.java
   ```
4. Start the server:
   ```bash
   java -cp ".;sqlite-jdbc.jar" BankApiServer
   ```
5. Open your web browser and navigate to `http://localhost:8080`.
