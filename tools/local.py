"""Native local development without Docker. Only manages this project's .local directory."""
from __future__ import annotations

import argparse
import http.client
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys

PROJECT = Path(__file__).resolve().parents[1]
LOCAL = PROJECT / '.local'
DATA = LOCAL / 'postgres'
PORT = int(os.environ.get('LOCAL_DB_PORT', '55432'))
USER = 'walletpay_local'
DATABASE = 'walletpay'
WINDOWS = os.name == 'nt'
FLAGS = subprocess.CREATE_NO_WINDOW if WINDOWS else 0


def pg_bin():
    configured = os.environ.get('POSTGRES_BIN')
    if configured:
        directory = Path(configured)
    elif shutil.which('pg_ctl'):
        directory = Path(shutil.which('pg_ctl')).parent
    elif WINDOWS:
        candidates = sorted(Path('C:/Program Files/PostgreSQL').glob('*/bin'), reverse=True)
        if not candidates:
            raise RuntimeError('Install PostgreSQL or set POSTGRES_BIN to its bin directory.')
        directory = candidates[0]
    else:
        candidates = sorted(Path('/usr/lib/postgresql').glob('*/bin'), reverse=True)
        if not candidates:
            raise RuntimeError('Install PostgreSQL server tools or set POSTGRES_BIN.')
        directory = candidates[0]
    return directory


def pg(name):
    return str(pg_bin() / (name + ('.exe' if WINDOWS else '')))


def configuration():
    LOCAL.mkdir(exist_ok=True)
    target = LOCAL / 'runtime.json'
    if target.exists():
        return json.loads(target.read_text(encoding='utf-8'))
    config = {'databasePassword': secrets.token_urlsafe(32),
              'jwtSecret': secrets.token_urlsafe(64),
              'webhookSecret': secrets.token_urlsafe(48)}
    target.write_text(json.dumps(config, indent=2), encoding='utf-8')
    target.chmod(0o600)
    return config


def database_environment(config):
    return dict(os.environ, PGPASSWORD=config['databasePassword'], PGHOST='127.0.0.1',
                PGPORT=str(PORT), PGUSER=USER)


def pg_run(args, config, check=True):
    with (LOCAL / 'postgres-control.log').open('a', encoding='utf-8') as log:
        result = subprocess.run(args, env=database_environment(config), stdout=log,
                                stderr=subprocess.STDOUT, timeout=120, creationflags=FLAGS)
    if check and result.returncode:
        raise RuntimeError('PostgreSQL command failed. Check .local/postgres-control.log.')
    return result.returncode


def database_start():
    config = configuration()
    if DATA.exists() and pg_run([pg('pg_ctl'), '-D', str(DATA), 'status'], config, False) == 0:
        print(f'Project PostgreSQL is already running on 127.0.0.1:{PORT}.')
        return
    with socket.socket() as probe:
        if probe.connect_ex(('127.0.0.1', PORT)) == 0:
            raise RuntimeError(f'Port {PORT} is occupied. Set LOCAL_DB_PORT to an unused port; existing servers are never modified.')
    if not (DATA / 'PG_VERSION').exists():
        password_file = LOCAL / 'initdb-password'
        password_file.write_text(config['databasePassword'], encoding='utf-8')
        password_file.chmod(0o600)
        try:
            pg_run([pg('initdb'), '-D', str(DATA), '-U', USER, '--auth=scram-sha-256',
                    '--pwfile=' + str(password_file), '--encoding=UTF8', '--locale=C'], config)
        finally:
            password_file.unlink(missing_ok=True)
    pg_run([pg('pg_ctl'), '-D', str(DATA), '-l', str(LOCAL / 'postgres.log'),
            '-o', f'-p {PORT} -h 127.0.0.1', '-w', 'start'], config)
    result = subprocess.run([pg('psql'), '-d', 'postgres', '-Atc',
                            "SELECT 1 FROM pg_database WHERE datname='walletpay'"],
                           env=database_environment(config), capture_output=True, text=True,
                           timeout=15, creationflags=FLAGS)
    if result.returncode:
        raise RuntimeError('Project database connection failed. Check .local/postgres.log.')
    if result.stdout.strip() != '1':
        pg_run([pg('createdb'), DATABASE], config)
    print(f'Project PostgreSQL is ready on 127.0.0.1:{PORT}. Credentials stay in ignored .local/runtime.json.')


def java_environment(config):
    env = dict(os.environ)
    if WINDOWS and not env.get('JAVA_HOME') and Path('C:/Program Files/Java/jdk-21').exists():
        env['JAVA_HOME'] = 'C:/Program Files/Java/jdk-21'
    if env.get('JAVA_HOME'):
        env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env['PATH']
    env.update(DATABASE_URL=f'jdbc:postgresql://127.0.0.1:{PORT}/{DATABASE}',
               DATABASE_USERNAME=USER, DATABASE_PASSWORD=config['databasePassword'],
               JWT_SECRET=config['jwtSecret'], WEBHOOK_SECRET=config['webhookSecret'],
               DEMO_FUNDING_ENABLED='true',
               SANDBOX_WEBHOOK_ENABLED='true', COOKIE_SECURE='false',
               ALLOWED_ORIGINS='http://localhost:5173,http://127.0.0.1:5173',
               SPRING_PROFILES_ACTIVE='local', JAVA_TOOL_OPTIONS='-Duser.timezone=UTC')
    return env


def launch(command, directory, env=None):
    if WINDOWS:
        command = ['cmd.exe', '/d', '/c'] + command
    return subprocess.call(command, cwd=directory, env=env)


def http_ready(port, path='/', require_health=False):
    connection = http.client.HTTPConnection('127.0.0.1', port, timeout=2)
    try:
        connection.request('GET', path)
        response = connection.getresponse()
        body = response.read(8193)
        if response.status != 200 or len(body) > 8192:
            return False
        if require_health:
            health = json.loads(body)
            return isinstance(health, dict) and health.get('status') == 'UP'
        return True
    except (OSError, http.client.HTTPException, ValueError, UnicodeError):
        return False
    finally:
        connection.close()


def service_status():
    # Read-only probes: no credentials, database commands or process startup.
    try:
        with socket.create_connection(('127.0.0.1', PORT), timeout=2):
            database_ready = True
    except OSError:
        database_ready = False
    checks = [
        (f'Database port 127.0.0.1:{PORT} (TCP only)', database_ready),
        ('Backend http://127.0.0.1:8080/actuator/health',
         http_ready(8080, '/actuator/health', require_health=True)),
        ('Frontend http://127.0.0.1:5173 (HTTP only)', http_ready(5173)),
    ]
    for label, ready in checks:
        print(f'{"READY" if ready else "NOT READY"}: {label}')
    if not all(ready for _, ready in checks):
        print('Start db-start, backend and frontend in separate terminals. See docs/LOCAL_DEVELOPMENT.md.')
        return 1
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['doctor', 'status', 'db-start', 'db-stop', 'backend', 'frontend'])
    args = parser.parse_args()
    if args.command == 'doctor':
        for name in ['java', 'mvn', 'node', 'npm', 'git']:
            print(f'{name}: {shutil.which(name) or "not on PATH"}')
        try:
            print(f'PostgreSQL tools: {pg_bin()}')
        except RuntimeError as error:
            print(error)
        print('Run db-start, then backend and frontend in separate terminals. No AWS account or Docker required.')
    elif args.command == 'status':
        raise SystemExit(service_status())
    elif args.command == 'db-start':
        database_start()
    elif args.command == 'db-stop':
        if not (DATA / 'PG_VERSION').exists():
            print('No project-managed database exists.')
            return
        if pg_run([pg('pg_ctl'), '-D', str(DATA), 'status'], configuration(), False) != 0:
            print('This project database is already stopped. Data was retained.')
            return
        pg_run([pg('pg_ctl'), '-D', str(DATA), '-m', 'fast', '-w', 'stop'], configuration())
        print('Stopped this project database. Data was retained.')
    elif args.command == 'backend':
        database_start()
        raise SystemExit(launch(['mvn.cmd' if WINDOWS else 'mvn', 'spring-boot:run'],
                               PROJECT / 'backend', java_environment(configuration())))
    elif args.command == 'frontend':
        raise SystemExit(launch(['npm.cmd' if WINDOWS else 'npm', 'run', 'dev'], PROJECT / 'frontend'))


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
