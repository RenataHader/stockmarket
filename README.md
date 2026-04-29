# Stock Market Simulator

A simplified stock market REST API built with **Java 21 + Spring Boot**, backed by **Redis**, deployed as a highly available multi-instance service behind an Nginx load balancer.

---

## Architecture

```
Client
  │
  ▼
Nginx (load balancer)
  ├── app1 (Spring Boot)
  └── app2 (Spring Boot)
        │
        ▼
      Redis
```

Two application instances run in parallel. If one is killed (e.g. via `POST /chaos`), Nginx automatically routes traffic to the surviving instance with no downtime. Redis serves as the shared state store for both instances.

---

## Requirements

- [Docker](https://docs.docker.com/get-docker/) with Docker Compose

No local Java installation needed — the build runs entirely inside Docker.

---

## Running

```bash
docker compose up --build
```

The API will be available at `http://localhost:8080`. Replace `8080` with any port you prefer.

---

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/wallets/{wallet_id}/stocks/{stock_name}` | Buy or sell a single stock (`{"type": "buy\|sell"}`) |
| `GET` | `/wallets/{wallet_id}` | Get wallet state |
| `GET` | `/wallets/{wallet_id}/stocks/{stock_name}` | Get quantity of a stock in a wallet |
| `GET` | `/stocks` | Get bank state |
| `POST` | `/stocks` | Set bank state |
| `GET` | `/log` | Get audit log of successful operations |
| `POST` | `/chaos` | Kill the instance handling this request |

---

## Design Decisions

**High availability** — Nginx is configured with `proxy_next_upstream` so failed requests are retried on the other instance automatically. Both instances use `restart: always`.

**Atomicity** — Buy, sell, and bank-set operations are implemented as Redis Lua scripts, which Redis executes atomically. This prevents race conditions between the two instances under concurrent load.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Storage | Redis 7 |
| Load Balancer | Nginx |
| Containerization | Docker + Docker Compose |
| Build | Maven |
