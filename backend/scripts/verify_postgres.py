"""Run HTTP acceptance checks on an isolated PostgreSQL database, then drop it.

Prerequisites: Java 21, Python 3.9+, running Compose postgres, built backend JAR.
Default: migrate KR1 schema + demo rows. --fresh: migrate an empty database.
--backup PATH: migrate an existing pg_dump custom-format backup instead.
The application's actual database and bootstrap administrator are never changed.
"""
import argparse
import base64
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--backup", type=Path)
    source.add_argument("--fresh", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    jar = root / "backend/target/repairly-backend-1.0.0.jar"
    if not jar.exists():
        parser.error("Build first: ./mvnw -f backend/pom.xml package")
    config = {}
    for line in (root / ".env").read_text(encoding="utf-8-sig").splitlines():
        if line.strip() and not line.lstrip().startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            config[key.strip()] = value.strip().strip("\"'")
    db_user = config.get("DB_USER", "repairly")
    db_port = config.get("DB_PORT", "55434")
    database = "repairly_verify_" + uuid.uuid4().hex[:12]
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]

    def db(*arguments, **kwargs):
        return subprocess.run(
            ["docker", "compose", "exec", "-T", "postgres", *arguments],
            cwd=root, check=True, **kwargs,
        )

    def sql_file(path):
        with path.open("rb") as stream:
            db("psql", "-v", "ON_ERROR_STOP=1", "-U", db_user, "-d", database, stdin=stream)

    env = os.environ.copy()
    env.update(
        SPRING_DATASOURCE_URL=f"jdbc:postgresql://127.0.0.1:{db_port}/{database}",
        SPRING_DATASOURCE_USERNAME=db_user,
        SPRING_DATASOURCE_PASSWORD=config["DB_PASSWORD"],
        SPRING_PROFILES_ACTIVE="",
        SERVER_ADDRESS="127.0.0.1",
        SERVER_PORT=str(port),
        JWT_SECRET_BASE64=base64.b64encode(secrets.token_bytes(48)).decode(),
        BOOTSTRAP_ADMIN_EMAIL="check@verify.local",
        BOOTSTRAP_ADMIN_PASSWORD="Boot!" + secrets.token_hex(16),
        PERMANENT_DELETE_ENABLED="true",
    )
    env.update(
        VERIFY_URL=f"http://127.0.0.1:{port}",
        VERIFY_EMAIL=env["BOOTSTRAP_ADMIN_EMAIL"],
        VERIFY_PASSWORD=env["BOOTSTRAP_ADMIN_PASSWORD"],
    )
    process = None
    log_path = root / "backend/target/verification.log"
    db("createdb", "-U", db_user, database)
    print(f"Temporary database: {database}; server log: {log_path}", flush=True)
    try:
        if args.backup:
            with args.backup.resolve().open("rb") as stream:
                db("pg_restore", "--exit-on-error", "-U", db_user, "-d", database, stdin=stream)
        elif not args.fresh:
            sql_file(root / "src/main/resources/db/migration/initial.sql")
            sql_file(root / "src/main/resources/db/migration/seed.sql")
        with log_path.open("w", encoding="utf-8") as log:
            process = subprocess.Popen(
                ["java", "-jar", str(jar)], cwd=root, env=env,
                stdout=log, stderr=subprocess.STDOUT,
            )
            for _ in range(120):
                if process.poll() is not None:
                    raise RuntimeError(f"Backend startup failed; inspect {log_path}")
                try:
                    with urllib.request.urlopen(env["VERIFY_URL"] + "/actuator/health", timeout=1) as response:
                        if response.status == 200:
                            break
                except (urllib.error.URLError, TimeoutError):
                    time.sleep(1)
            else:
                raise RuntimeError(f"Backend startup timed out; inspect {log_path}")
            if args.fresh:
                sql_file(root / "src/main/resources/db/migration/seed.sql")
            subprocess.run([sys.executable, str(root / "backend/scripts/verify_api.py")], env=env, check=True)
    finally:
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        db("dropdb", "-U", db_user, database)
        print("Temporary database removed. Application database unchanged.")


if __name__ == "__main__":
    main()
