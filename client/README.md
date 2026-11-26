# Harborline Health · Analyst Knowledge Portal

A sample **client application** for the knowledgeDrop service. It plays the part of an internal portal at a
fictional health insurer, _Harborline Health_. Analysts and customer-service staff use it to ask questions
about internal documentation, such as "Can you tell me about 340B claims?", and to manage the shared
document library.

> Harborline Health is fictional. The documents in `sample-docs/` are made-up demo content.

This folder is self-contained. It does **not** change the Java service and uses only its existing
endpoints. There is no custom middleware:

```
browser ──► nginx (:3000) ──┬─► /knowledgeDrop/*  knowledgeDrop service (:8080)   [existing REST API]
                            └─► /db/*            PostgREST ──► Postgres schema insurance_portal
                                                              (same Postgres instance as the service)
```

- **knowledgeDrop** stores and searches the documents. The portal registers one knowledgeDrop _client_
  for the whole company, and every analyst uses that client's token.
- **The `insurance_portal` schema** stores the portal's own data: analyst accounts, question history,
  saved answers, who uploaded each document, and the knowledgeDrop token. It sits in the same database as
  the service but in its own schema. Hibernate only manages `public`, so the two never collide.
- **PostgREST** turns that schema into a REST API. Authorization is enforced _in the database_ with roles,
  column grants and row-level security, keyed off a JWT that the `login` function signs.

## Roles

|                                                              | Admin      | Analyst (user) |
| ------------------------------------------------------------ | ---------- | -------------- |
| Ask questions                                                | ✓          | ✓              |
| Question history and saved answers                           | everyone's | **own only**   |
| Browse and download every document                           | ✓          | ✓              |
| Upload and delete documents                                  | ✓          | ✓              |
| See who uploaded each document                               | ✓          | ✓              |
| Register the knowledgeDrop client, view or replace its token | ✓          | –              |
| Create, disable and reset analyst accounts                   | ✓          | –              |

The first admin is created from `PORTAL_ADMIN_USER` and `PORTAL_ADMIN_PASSWORD` in `.env`. That admin then
creates everyone else on the **Analyst accounts** page.

## Known limitations

- **Ask returns 501 today.** `POST /knowledgeDrop/ask` is still a stub in the service (`AskService`).
  The portal calls the real endpoint and shows "The Ask feature isn't available on this knowledgeDrop server
  yet". The attempt is still saved to history. Once the service implements `/ask`, answers and citations
  appear with no portal changes.
- **Document delete may return 501.** The service soft-deletes the document and commits, then returns
  501 while search-index cleanup is unimplemented. The portal treats that as "deleted (cleanup pending)".
- **The knowledgeDrop token is hidden in the UI, but it is not a secret from analysts.** The browser needs
  it to call the service, so any signed-in analyst could find it in devtools. Only admins can read the
  `settings` table or see the token in the UI. Keeping it fully server-side would need a proxy that injects
  the header. That is out of scope for this demo.
- Owner tracking covers uploads made **through the portal**. Documents added another way show "Unknown".

## Running it locally

Prerequisites: Docker, Node 20+, and the knowledgeDrop service running (see the root README).

1. Start the shared infrastructure and the service from the repo root:

   ```powershell
   docker compose up -d
   ```

   ```powershell
   ./mvnw spring-boot:run
   ```

2. Configure the portal (from `client/`):

   ```powershell
   Copy-Item .env.example .env
   ```

   Edit `.env` and change the passwords and `PORTAL_JWT_SECRET`, which must be at least 32 characters.

### Option A: server mode (same host, different ports)

```powershell
docker compose up -d --build
```

Open <http://localhost:3000>. nginx serves the built app and proxies both backends. PostgREST is not
published to the host.

### Option B: dev mode (hot reload)

```powershell
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db-init postgrest
```

```powershell
npm install
```

```powershell
npm run dev
```

Open <http://localhost:5173>. Vite proxies `/knowledgeDrop` to `:8080` and `/db` to PostgREST on `:3001`.

### First run

1. Sign in with the admin account from `.env`.
2. **knowledgeDrop setup** → _Register client_, named "Harborline Health" by default. The token is saved
   to the portal.
3. **Analyst accounts** → create a few analysts.
4. **Upload** → add the files in `sample-docs/` and wait for each one to reach _ready_.
5. **Ask** → for example "What are the submission requirements for 340B claims?"

Re-running `docker compose up` re-applies `db/init.sql`. The script is idempotent, keeps all data, and
never resets an existing admin's password.

## Moving to a shared server later

Run the root stack and the service as usual, then run `docker compose up -d --build` in `client/` on the
same machine. Set `KD_API_URL` if the service is not on `host.docker.internal:8080`. The service deployed
on port 80, for example, would use `http://host.docker.internal:80`. Then open `CLIENT_PORT` in the
host firewall.

## Scripts

| Command                           | What it does                    |
| --------------------------------- | ------------------------------- |
| `npm run dev`                     | Vite dev server                 |
| `npm run build`                   | Type-check and build to `dist/` |
| `npm test`                        | Vitest unit and component tests |
| `npm run lint`                    | ESLint                          |
| `npm run format` / `format:check` | Prettier                        |

## Layout

```
db/init.sql            schema, roles, RLS policies, login/JWT and admin RPCs
docker-compose*.yml    db-init (applies init.sql), postgrest, web (nginx)
nginx/                 nginx config template (SPA + proxies)
sample-docs/           fictional insurance documents for the demo
src/api/               knowledgeDrop and PostgREST wrappers + types
src/auth/              session (JWT), context, route guards
src/pages/             Ask, History, Saved, Library, Upload, Account, admin/*
```
