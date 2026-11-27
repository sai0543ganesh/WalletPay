"""Exercise a running WalletPay sandbox against its real database.

Creates disposable test users and sandbox transactions. Never use against a live-money system.
Set API_BASE and WEBHOOK_SECRET to the isolated application under test.
"""
from __future__ import annotations

import concurrent.futures
import copy
import hashlib
import hmac
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import time
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get('API_BASE', 'http://127.0.0.1:8080/api/v1').rstrip('/')
SECRET = os.environ.get('WEBHOOK_SECRET', os.environ.get('SANDBOX_WEBHOOK_SECRET', ''))
RESULTS: list[dict] = []


class Client:
    def __init__(self):
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))

    def fork(self):
        other = Client()
        for cookie in self.cookies:
            other.cookies.set_cookie(copy.copy(cookie))
        return other

    def request(self, method, path, body=None, headers=None, csrf=True, raw=False):
        hdr = dict(headers or {})
        if method not in ('GET', 'HEAD', 'OPTIONS') and csrf:
            status, token, _ = self.request('GET', '/auth/csrf')
            assert status == 200, 'CSRF endpoint unavailable'
            hdr[token['headerName']] = token['token']
        payload = body if isinstance(body, bytes) else json.dumps(body).encode() if body is not None else None
        if payload is not None:
            hdr.setdefault('Content-Type', 'application/json')
        request = urllib.request.Request(BASE + path, data=payload, method=method, headers=hdr)
        try:
            response = self.opener.open(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        data = response.read()
        decoded = data.decode('utf-8')
        if raw:
            return response.code, decoded, response.headers
        try:
            decoded = json.loads(decoded) if decoded else None
        except json.JSONDecodeError:
            pass
        return response.code, decoded, response.headers


def expect(name, condition):
    RESULTS.append({'check': name, 'passed': bool(condition)})
    if not condition:
        raise AssertionError(name)


def key():
    return {'Idempotency-Key': str(uuid.uuid4())}


def balance(client):
    status, value, _ = client.request('GET', '/wallet')
    assert status == 200
    return value['balanceMinor']


def register(name):
    client = Client()
    phone = '9' + ''.join(secrets.choice('0123456789') for _ in range(9))
    password = 'Local-test-' + secrets.token_hex(12) + '!9'
    status, user, headers = client.request('POST', '/auth/register', {'fullName': name, 'phone': phone, 'password': password})
    expect(f'{name}: registration', status == 201 and user.get('phone') == phone)
    expect(f'{name}: HttpOnly session cookie', any('httponly' in c.lower() for c in headers.get_all('Set-Cookie', [])))
    expect(f'{name}: zero opening balance', balance(client) == 0)
    return client, phone, password, user


def webhook(client, deposit, event=None, timestamp=None, secret=None, amount=100_000):
    stamp = str(timestamp if timestamp is not None else int(time.time()))
    body = json.dumps({'eventId': event or str(uuid.uuid4()), 'depositId': deposit,
                       'amountMinor': amount, 'currency': 'INR', 'status': 'COMPLETED'}, separators=(',', ':')).encode()
    signature = hmac.new((SECRET if secret is None else secret).encode(), stamp.encode() + b'.' + body, hashlib.sha256).hexdigest()
    return client.request('POST', '/webhooks/sandbox', body, {
        'X-Webhook-Timestamp': stamp, 'X-Webhook-Signature': signature}, csrf=False)


def main():
    if not SECRET:
        raise SystemExit('Set WEBHOOK_SECRET for this isolated sandbox test; no real provider is contacted.')
    anonymous = Client()
    expect('unauthorized wallet blocked', anonymous.request('GET', '/wallet')[0] == 401)
    expect('registration rejects invalid fields', anonymous.request('POST', '/auth/register', {
        'fullName': '', 'phone': 'x', 'password': 'x'})[0] == 400)
    alice, alice_phone, password, user = register('Smoke Alice')
    bob, bob_phone, _, _ = register('Smoke Bob')
    stranger, _, _, _ = register('Smoke Carol')
    expect('password not returned', 'password' not in json.dumps(user).lower())
    expect('missing CSRF blocked', alice.request('POST', '/deposits', {
        'amountMinor': 100_000, 'provider': 'SANDBOX'}, key(), csrf=False)[0] == 403)

    deposit_key = key()
    status, deposit, _ = alice.request('POST', '/deposits', {'amountMinor': 100_000, 'provider': 'SANDBOX'}, deposit_key)
    expect('deposit remains pending', status == 201 and deposit['status'] == 'PENDING' and balance(alice) == 0)
    again = alice.request('POST', '/deposits', {'amountMinor': 100_000, 'provider': 'SANDBOX'}, deposit_key)
    expect('deposit creation idempotent', again[0] in (200, 201) and again[1]['id'] == deposit['id'])
    expect('unsigned/invalid webhook blocked', webhook(anonymous, deposit['id'], secret='incorrect')[0] in (400, 401, 403))
    expect('invalid webhook does not credit', balance(alice) == 0)
    expect('expired webhook blocked', webhook(anonymous, deposit['id'], timestamp=int(time.time()) - 600)[0] in (400, 401, 403))
    expect('webhook amount mismatch rejected', webhook(anonymous, deposit['id'], amount=100_001)[0] in (400, 409, 422))
    event = str(uuid.uuid4())
    expect('valid authenticated sandbox event accepted', webhook(anonymous, deposit['id'], event=event)[0] in (200, 204))
    expect('sandbox event credits exact amount', balance(alice) == 100_000)
    expect('replayed sandbox event handled safely', webhook(anonymous, deposit['id'], event=event)[0] in (200, 204, 409))
    expect('replay never double-credits', balance(alice) == 100_000)

    payload = {'recipientPhone': bob_phone, 'amountMinor': 25_000, 'note': '=SUM(1,2)'}
    transfer_key = key()
    status, transfer, _ = alice.request('POST', '/transfers', payload, transfer_key)
    expect('transfer succeeds', status == 201 and transfer['status'] == 'COMPLETED')
    expect('transfer conserves funds', balance(alice) == 75_000 and balance(bob) == 25_000)
    replay = alice.request('POST', '/transfers', payload, transfer_key)
    expect('transfer idempotency returns original', replay[0] in (200, 201) and replay[1]['id'] == transfer['id'])
    expect('transfer replay never double-debits', balance(alice) == 75_000)
    expect('changed idempotent payload rejected', alice.request('POST', '/transfers', dict(payload, amountMinor=25_001), transfer_key)[0] == 409)
    expect('self-transfer rejected', alice.request('POST', '/transfers', dict(payload, recipientPhone=alice_phone), key())[0] in (400, 409, 422))
    for amount in (0, -1, 9223372036854775808):
        expect(f'invalid transfer amount rejected: {amount}', alice.request('POST', '/transfers', dict(payload, amountMinor=amount), key())[0] == 400)
    expect('insufficient balance rejected', alice.request('POST', '/transfers', dict(payload, amountMinor=100_000), key())[0] in (400, 409, 422))
    expect('rejected transfer rolled back', balance(alice) == 75_000)

    def transfer_once(_):
        return alice.fork().request('POST', '/transfers', dict(payload, amountMinor=10_000, note='Concurrent test'), key())[0]
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        statuses = list(pool.map(transfer_once, range(10)))
    expect('concurrent transfers complete or reject cleanly', all(s in (200, 201, 400, 409, 422, 429) for s in statuses))
    expect('concurrent balance never negative', balance(alice) >= 0)
    expect('concurrent transfers conserve total funds', balance(alice) + balance(bob) == 100_000)

    receipt = '/transactions/' + transfer['id'] + '/receipt'
    expect('sender receipt allowed', alice.request('GET', receipt)[0] == 200)
    expect('recipient receipt allowed', bob.request('GET', receipt)[0] == 200)
    expect('unrelated receipt blocked', stranger.request('GET', receipt)[0] in (403, 404))
    status, page, _ = alice.request('GET', '/transactions?page=0&size=2')
    expect('history paginated', status == 200 and len(page['items']) <= 2 and page['totalItems'] >= 2)
    filtered = alice.request('GET', '/transactions?search=Concurrent&type=TRANSFER&direction=DEBIT&status=COMPLETED')[1]
    expect('history filters respected', len(filtered['items']) > 0 and all(t['type'] == 'TRANSFER' and t['direction'] == 'DEBIT' and t['status'] == 'COMPLETED' for t in filtered['items']))
    status, csv, headers = alice.request('GET', '/transactions/export', raw=True)
    expect('CSV export works', status == 200 and 'text/csv' in headers.get('Content-Type', ''))
    expect('CSV formula injection escaped', '=SUM(1,2)' not in csv or "'=SUM(1,2)" in csv or '\t=SUM(1,2)' in csv)
    expect('analytics available', alice.request('GET', '/analytics?days=30')[0] == 200)
    status, profile, _ = alice.request('PUT', '/profile', {'fullName': 'Smoke Alice Updated'})
    expect('profile updated', status == 200 and profile['fullName'] == 'Smoke Alice Updated')
    forbidden = alice.request('OPTIONS', '/wallet', headers={'Origin': 'https://untrusted.invalid', 'Access-Control-Request-Method': 'GET'})
    expect('untrusted credentialed CORS not allowed', forbidden[2].get('Access-Control-Allow-Origin') != 'https://untrusted.invalid')
    old_session = alice.fork()
    expect('logout succeeds', alice.request('POST', '/auth/logout')[0] == 204)
    expect('logout revokes copied session', old_session.request('GET', '/wallet')[0] == 401)
    expect('wrong login password rejected', alice.request('POST', '/auth/login', {'phone': alice_phone, 'password': password + 'wrong'})[0] == 401)
    expect('login succeeds with correct password', alice.request('POST', '/auth/login', {'phone': alice_phone, 'password': password})[0] == 200)
    old_session = alice.fork()
    expect('password update accepted', alice.request('PUT', '/profile/password', {'currentPassword': password, 'newPassword': password + 'New'})[0] == 204)
    expect('password change revokes prior session', old_session.request('GET', '/wallet')[0] == 401)


if __name__ == '__main__':
    failure = None
    try:
        main()
    except Exception as error:
        failure = type(error).__name__ + ': ' + str(error)
    result = {'checks': RESULTS, 'passed': sum(r['passed'] for r in RESULTS),
              'failed': sum(not r['passed'] for r in RESULTS), 'error': failure,
              'scope': 'Actual HTTP application and isolated local PostgreSQL; no real payment provider'}
    output = Path(os.environ.get('SMOKE_REPORT', '.local/api-smoke.json'))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps({'passed': result['passed'], 'failed': result['failed'], 'error': failure, 'report': str(output)}))
    raise SystemExit(1 if failure else 0)
