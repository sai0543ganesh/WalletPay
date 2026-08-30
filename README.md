# Wallet Pay

A full-stack digital wallet demo built with **Spring Boot, React/Vite and PostgreSQL**.

## Local development

### 1. Database

```powershell
docker compose up -d
```

PostgreSQL is available locally at `localhost:5433`.

### 2. Backend
## System Architecture

Wallet Pay is designed as a full-stack application with three main layers:

```text
┌──────────────────────────────────────────────────────────────┐
│                        USER / BROWSER                        │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                     REACT + VITE FRONTEND                    │
│                                                              │
│  • Authentication UI                                         │
│  • Wallet Dashboard                                          │
│  • Balance Display                                           │
│  • Add Money                                                 │
│  • P2P Transfers                                             │
│  • Transaction History                                       │
└──────────────────────────────┬───────────────────────────────┘
                               │ HTTP / REST API
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                    SPRING BOOT BACKEND                       │
│                                                              │
│  • Authentication                                            │
│  • JWT Validation                                            │
│  • Wallet Management                                         │
│  • Balance Processing                                        │
│  • P2P Transfer Processing                                   │
│  • Transaction Management                                    │
│  • Business Logic                                            │
└──────────────────────────────┬───────────────────────────────┘
                               │ JPA / Hibernate
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                      POSTGRESQL DATABASE                     │
│                                                              │
│  • Users                                                     │
│  • Wallet Balances                                           │
│  • On-Ramp Transactions                                      │
│  • P2P Transactions                                          │
└──────────────────────────────────────────────────────────────┘

The frontend is responsible for user interaction and presentation. It communicates with the backend using REST APIs. The Spring Boot backend handles authentication, validation, wallet operations, transaction processing, and database operations. PostgreSQL provides persistent storage for users, balances, and transaction records.

Detailed Project Structure
wallet-pay-complete-updated/
│
├── src/
│   │
│   ├── main/
│   │   │
│   │   ├── java/
│   │   │   │
│   │   │   └── com/
│   │   │       └── walletpay/
│   │   │           │
│   │   │           ├── config/
│   │   │           │   └── Application configuration
│   │   │           │
│   │   │           ├── controller/
│   │   │           │   └── REST API endpoints
│   │   │           │
│   │   │           ├── service/
│   │   │           │   └── Business logic
│   │   │           │
│   │   │           ├── repository/
│   │   │           │   └── Database access layer
│   │   │           │
│   │   │           ├── entity/
│   │   │           │   └── Database entities
│   │   │           │
│   │   │           ├── dto/
│   │   │           │   └── Request and response objects
│   │   │           │
│   │   │           ├── security/
│   │   │           │   └── JWT and security configuration
│   │   │           │
│   │   │           └── WalletPayApplication.java
│   │   │
│   │   └── resources/
│   │       │
│   │       └── application.yml
│   │           └── Application configuration
│   │
│   └── test/
│       └── Backend tests
│
├── frontend/
│   │
│   ├── src/
│   │   │
│   │   ├── components/
│   │   │   └── Reusable UI components
│   │   │
│   │   ├── pages/
│   │   │   └── Application screens
│   │   │
│   │   ├── services/
│   │   │   └── Backend API communication
│   │   │
│   │   ├── context/
│   │   │   └── Global application state
│   │   │
│   │   ├── App.jsx
│   │   │   └── Main application component
│   │   │
│   │   └── main.jsx
│   │       └── Application entry point
│   │
│   ├── public/
│   │   └── Static frontend assets
│   │
│   ├── package.json
│   │   └── Frontend dependencies and scripts
│   │
│   └── vite.config.js
│       └── Vite configuration
│
├── docker-compose.yml
│   └── PostgreSQL Docker configuration
│
├── Dockerfile
│   └── Spring Boot container configuration
│
├── pom.xml
│   └── Backend dependencies and Maven configuration
│
└── README.md
    └── Project documentation
Backend Architecture

The Spring Boot backend follows a layered architecture:

Client Request
      │
      ▼
┌─────────────────┐
│   Controller    │
│   REST APIs     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│     Service     │
│  Business Logic │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   Repository    │
│ Database Access │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   PostgreSQL    │
└─────────────────┘
Controller Layer

The controller layer exposes REST API endpoints to the frontend. It receives HTTP requests, validates input, and forwards requests to the appropriate service layer.

Examples of functionality handled by the controller layer include:

User registration
User login
Retrieving wallet balances
Adding money
Sending money to another user
Retrieving transaction history
Service Layer

The service layer contains the application's core business logic.

For example, during a peer-to-peer transfer, the service layer:

Identifies the sender.
Identifies the recipient.
Validates the transfer amount.
Checks whether the sender has sufficient funds.
Updates the wallet balances.
Creates transaction records.
Ensures database operations are completed consistently.

This separation keeps business logic independent from the API and database layers.

Repository Layer

The repository layer communicates with PostgreSQL using Spring Data JPA.

It handles operations such as:

Finding users
Retrieving wallet information
Saving transactions
Updating balances
Retrieving transaction history

Spring Data JPA and Hibernate reduce the amount of manual SQL required in the application.

Frontend Architecture

The frontend is built using React and Vite.

Browser
   │
   ▼
main.jsx
   │
   ▼
App.jsx
   │
   ├── Login / Authentication
   │
   ├── Dashboard
   │      │
   │      ├── Balance
   │      ├── Quick Actions
   │      └── Recent Transactions
   │
   ├── Add Money
   │
   ├── Send Money
   │
   └── Transaction History

The frontend is responsible for:

Displaying application pages
Managing user interaction
Sending API requests
Displaying wallet information
Showing transaction results
Managing authenticated user state

The frontend does not directly access the database. All data access occurs through the Spring Boot backend.

Authentication Flow
User enters credentials
        │
        ▼
React Frontend
        │
        │ POST /login
        ▼
Spring Boot Backend
        │
        ▼
Validate credentials
        │
        ├── Invalid → Return authentication error
        │
        └── Valid
              │
              ▼
         Generate JWT
              │
              ▼
         Return JWT token
              │
              ▼
React stores authentication state
              │
              ▼
Authenticated API requests

Passwords are protected using BCrypt hashing, while JWT is used to identify authenticated users during API requests.

Wallet and Transaction Flow
Adding Money
User enters amount
        │
        ▼
Frontend
        │
        ▼
Backend creates transaction
        │
        ▼
Transaction is processed
        │
        ▼
Wallet balance is updated
        │
        ▼
Transaction stored in PostgreSQL
        │
        ▼
Updated balance displayed
Peer-to-Peer Transfer
Sender
   │
   │ Send Money
   ▼
Spring Boot Backend
   │
   ├── Validate sender
   │
   ├── Validate recipient
   │
   ├── Validate amount
   │
   ├── Check balance
   │
   ▼
Update sender wallet
   │
   ▼
Update recipient wallet
   │
   ▼
Create transaction records
   │
   ▼
Save to PostgreSQL
Database Design

The database stores the application's persistent information.

Users

Stores information required to identify and authenticate users.

User
├── id
├── phone
├── password
└── related wallet
Wallets

Stores user wallet information.

Wallet
├── id
├── user_id
├── amount
└── locked
Transactions

Stores financial activity performed through the application.

Transaction
├── id
├── sender
├── recipient
├── amount
├── status
└── timestamp

This structure ensures that balances and transactions remain available after the application is restarted.

Local Development Environment

The project runs locally using three services:

┌─────────────────────────────────────┐
│ React Frontend                      │
│ http://localhost:5173               │
└─────────────────┬───────────────────┘
                  │
                  │ REST API
                  ▼
┌─────────────────────────────────────┐
│ Spring Boot Backend                 │
│ http://localhost:8080               │
└─────────────────┬───────────────────┘
                  │
                  │ JPA / JDBC
                  ▼
┌─────────────────────────────────────┐
│ PostgreSQL                          │
│ localhost:5433                      │
└─────────────────────────────────────┘

This separation allows each part of the application to be independently developed, tested, and deployed.

Deployment Architecture

The application can be deployed with the same separation:

                    Internet
                       │
                       ▼
              Hosted React Frontend
                       │
                       │ HTTPS API Requests
                       ▼
             Hosted Spring Boot Backend
                       │
                       │ Secure Database Connection
                       ▼
                PostgreSQL Database

This architecture allows the frontend, backend, and database to be deployed independently while remaining connected through configured URLs and environment variables.
```powershell
mvn spring-boot:run
```

If a Windows/Java installation sends the obsolete `Asia/Calcutta` timezone to PostgreSQL, use this local workaround:

```powershell
$env:JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"
mvn spring-boot:run
```

The backend runs on `http://localhost:8080`.

### 3. Frontend

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Open `http://localhost:5173`.

Demo account:

- Phone: `0987654321`
- Password: any non-empty password


The frontend is then rebuilt and served with that API URL.

### Important security note

This is a demonstration wallet. Do not use real payment credentials or real money. Generate strong production secrets and never commit database credentials or JWT secrets to GitHub.
