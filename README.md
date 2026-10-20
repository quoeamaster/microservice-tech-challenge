# Pet Photo Microservice

A Spring Boot microservice that fetches random cat, dog or bear photos from public APIs, saves them in an embedded SQLite database, and returns the latest saved photo. A simple web UI is served by the same app.

**Stack:** Java 17 · Spring Boot 4.1 · Maven (wrapper included) · SQLite · JUnit 5 · Docker

## Run it

### From source (needs JDK 17+)

```bash
./start_app.sh                  # or: ./mvnw spring-boot:run
```

Or build the executable jar and run it. It has an embedded web server, so no Tomcat or other app server is needed:

```bash
./mvnw package
java -jar target/my-pet-photo-microservice-1.0-SNAPSHOT.jar
```

No Maven install is needed (the `mvnw` wrapper downloads it), and no database setup is needed (SQLite creates `data/pets.db` on first start).

### With Docker (no Java needed)

```bash
docker compose up --build
```

The image builds from source and runs the tests during the build. The database is kept in the `photo-data` volume; `docker compose down -v` deletes it.

Then open **http://localhost:9081**.

### Tests

```bash
./mvnw test
```

16 tests:
- **Service tests** against a mocked HTTP server and a real SQLite file: fallback, giving up when all sources fail, rejecting non-images, all-or-nothing batches.
- **API tests** of the whole app: status codes, CORS.

No network access is needed.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/photos/fetch` | **A.** Fetch photo(s) and save them. Optional JSON body `{"animal": "cat\|dog\|bear", "count": 1-10}`. With no body, it fetches 1 photo of a random animal |
| `GET` | `/api/photos/latest/image` | **B.** The latest saved photo as an image. Optional `?animal=cat` |
| `GET` | `/api/photos/latest/meta` | Its metadata (animal, source, timestamp, size) |
| `GET` | `/api/photos/{id}/image` | Any saved photo by id |

```bash
curl -X POST localhost:9081/api/photos/fetch -H 'Content-Type: application/json' -d '{"animal": "dog", "count": 2}'
curl -o latest.jpg localhost:9081/api/photos/latest/image
```

## Database schema

[`src/main/resources/schema.sql`](src/main/resources/schema.sql) runs at startup (`spring.sql.init.mode=always`):

```sql
CREATE TABLE IF NOT EXISTS fetched_photos (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    animal_type    TEXT NOT NULL CHECK (animal_type IN ('CAT', 'DOG', 'BEAR')),
    image_binary   BLOB NOT NULL,
    content_type   TEXT NOT NULL,
    timestamp      TEXT NOT NULL,   -- ISO-8601 UTC, when the photo was fetched
    source_website TEXT NOT NULL    -- full URL the photo was downloaded from
);
```

"Latest" is the highest `id`, i.e. the most recently inserted row.

## Photo sources and error handling

| Animal | Sources, tried in order |
|---|---|
| Cat | cataas.com, then placecats.com (placekitten.com from the brief is offline) |
| Dog | place.dog, then placedog.net |
| Bear | placebear.com |

| Situation | Behaviour |
|---|---|
| A source fails, times out (5 s connect / 10 s read) or returns something that isn't an image | Try the next source |
| Every source for that animal fails | `502 Bad Gateway` with the reason. **Each source is tried once**, so the request never hangs |
| One photo in a batch fails | Nothing from that request is saved |
| Bad animal, `count` outside 1–10, malformed JSON | `400 Bad Request` |
| Nothing saved yet | `404 Not Found` |

Each request uses a random image size, because these services return the same photo for the same size.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `9081` | HTTP port |
| `DB_URL` | `jdbc:sqlite:data/pets.db` | SQLite database location |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Origins allowed to call `/api/**` from a browser. The built-in UI doesn't need CORS |

## Setup guide (VS Code)

a. Install a JDK (17 or newer):
```bash
brew install openjdk@21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home   # add to ~/.zshrc
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Installing Maven (`brew install maven`) is optional; `./mvnw` downloads the right version automatically.

b. Install the VS Code extensions. Open the folder and accept the recommended extensions from `.vscode/extensions.json`, or install them manually:
1. Extension Pack for Java (Microsoft). It includes Language Support for Java (Red Hat) and Maven for Java.
2. Spring Boot Extension Pack (VMware)
3. Docker (Microsoft)

c. Open the project:
1. **File → Open Folder** and select this folder.
2. VS Code detects `pom.xml`, imports the Maven project, and downloads the dependencies into `~/.m2/repository`.
3. **The Maven config file is `pom.xml`.** Dependencies go in `<dependencies>`. The Spring Boot parent manages most versions, and `spring-boot-maven-plugin` builds the executable jar. Saving `pom.xml` re-imports the project automatically.

d. Run, debug and test:

| Task | VS Code | Terminal |
|---|---|---|
| Run | **Run** above `main` in `PetPhotoApplication.java`, or the Spring Boot Dashboard | `./mvnw spring-boot:run` |
| Debug | F5 with *Run PetPhotoApplication* (`.vscode/launch.json`) | — |
| Test | Testing view (flask icon) → Run All | `./mvnw test` |
| Build jar | Maven view → Lifecycle → `package` | `./mvnw package` |

## Folder structure

```bash
.
├── pom.xml, mvnw, mvnw.cmd, .mvn/      # Maven build + wrapper
├── Dockerfile, docker-compose.yml      # container build and run
├── start_app.sh                        # run from source
└── src/
    ├── main/java/com/challenge/        # application code
    ├── main/resources/
    │   ├── application.properties      # configuration
    │   ├── schema.sql                  # database schema
    │   └── static/index.html           # web UI
    └── test/java/com/challenge/        # tests
```
