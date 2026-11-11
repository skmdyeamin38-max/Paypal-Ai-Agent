# PayPal AI Agent (Sandbox)

A small Java command-line app that creates and captures **PayPal Sandbox** orders.
Gemini (via LangChain4j) is used for normal chat; payment commands are handled directly by the code.

> **Sandbox only.** No real money is used. Do not use Live credentials with this project.

## Requirements
- Java 17+
- Maven
- A Gemini API key (Google AI Studio)
- PayPal Sandbox app credentials (developer.paypal.com, Apps & Credentials, Sandbox)

## Setup
Set these environment variables (never hardcode or commit them).

Windows (PowerShell):
```
$env:GEMINI_API_KEY="your_gemini_key"
$env:PAYPAL_CLIENT_ID="your_sandbox_client_id"
$env:PAYPAL_CLIENT_SECRET="your_sandbox_secret"
```

Mac / Linux:
```
export GEMINI_API_KEY=your_gemini_key
export PAYPAL_CLIENT_ID=your_sandbox_client_id
export PAYPAL_CLIENT_SECRET=your_sandbox_secret
```

## Run
```
mvn clean compile exec:java "-Dexec.mainClass=App"
```

Test PayPal alone (no AI):
```
mvn clean compile exec:java "-Dexec.mainClass=App" "-Dexec.args=--test"
```

## Usage
```
User: create payment 10 USD
AI Agent: Create a Sandbox order for $10.00 USD? (yes/no): yes
```
Open the approval link, log in with a **Sandbox Personal (buyer)** account and approve.
Then capture the order:
```
User: capture <ORDER_ID>
```
The Order ID is also in the return URL after approval (`?token=<ORDER_ID>`).

Type `exit` to quit.

## Notes
- Amounts are limited to 0 to 1000 USD.
- The return and cancel URLs point to example.com (testing only).s