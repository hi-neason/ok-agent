#!/usr/bin/env bash
set -euo pipefail
# Starts only a disposable local process. Never reads application or machine MySQL configuration.
if [ -n "${MYSQL_BIN:-}" ]; then export PATH="$MYSQL_BIN:$PATH"; fi
for tool in mysqld mysql mysqladmin python3 openssl; do
  command -v "$tool" >/dev/null || { echo "Required local executable is missing: $tool" >&2; exit 1; }
done
migration_dir="$(mktemp -d /tmp/ok-agent-mysql.XXXXXXXX)"
migration_password="$(openssl rand -hex 24)"
migration_pid=''
cleanup() {
  if [ -n "$migration_pid" ]; then
    MYSQL_PWD="$migration_password" mysqladmin --no-defaults --socket="$migration_dir/mysql.sock" -u root shutdown >/dev/null 2>&1 || kill "$migration_pid" 2>/dev/null || true
    wait "$migration_pid" 2>/dev/null || true
  fi
  rm -rf "$migration_dir"
}
trap cleanup EXIT
mysqld --no-defaults --initialize-insecure --datadir="$migration_dir/data" --log-error="$migration_dir/init.log"
port="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')"
mysqld --no-defaults --datadir="$migration_dir/data" --socket="$migration_dir/mysql.sock" \
  --pid-file="$migration_dir/mysql.pid" --port="$port" --bind-address=127.0.0.1 \
  --mysqlx=0 --skip-log-bin --innodb-buffer-pool-size=64M --log-error="$migration_dir/server.log" &
migration_pid=$!
for attempt in $(seq 1 60); do
  if mysqladmin --no-defaults --socket="$migration_dir/mysql.sock" -u root ping --silent >/dev/null 2>&1; then break; fi
  if ! kill -0 "$migration_pid" 2>/dev/null || [ "$attempt" -eq 60 ]; then
    cat "$migration_dir/server.log" >&2; exit 1
  fi
  sleep 1
done
# The generated value is test-only and never written to the application configuration.
printf "ALTER USER 'root'@'localhost' IDENTIFIED BY '%s';\n" "$migration_password" | mysql --no-defaults --socket="$migration_dir/mysql.sock" -u root
export OK_AGENT_MIGRATION_TEST_URL="jdbc:mysql://127.0.0.1:$port/"
export OK_AGENT_MIGRATION_TEST_PASSWORD="$migration_password"
mvn -f backend/pom.xml -Dtest=MySqlMigrationIT -Dsurefire.failIfNoSpecifiedTests=false test
