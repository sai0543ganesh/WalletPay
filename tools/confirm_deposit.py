"""Sign a sandbox deposit callback for local testing. This is not a bank integration."""
import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('deposit_id', type=uuid.UUID)
parser.add_argument('amount_minor', type=int, help='Exact amount requested, in paise, e.g. 10000 for INR 100')
parser.add_argument('--base-url', default='http://127.0.0.1:8080/api/v1')
args = parser.parse_args()
if not 1 <= args.amount_minor <= 100_000_000:
    parser.error('Amount must be between 1 and 100,000,000 paise.')
if urllib.parse.urlparse(args.base_url).hostname not in ('127.0.0.1', 'localhost', '::1'):
    parser.error('This helper only sends to a local sandbox. Use a verified provider integration for deployed funding.')
secret = os.environ.get('WEBHOOK_SECRET')
config_path = Path(__file__).resolve().parents[1] / '.local/runtime.json'
if not secret and config_path.exists():
    secret = json.loads(config_path.read_text(encoding='utf-8'))['webhookSecret']
if not secret:
    parser.error('Start the native local environment or set WEBHOOK_SECRET. Never paste it into the browser.')
body = json.dumps({'eventId': str(uuid.uuid4()), 'depositId': str(args.deposit_id),
                   'amountMinor': args.amount_minor, 'currency': 'INR', 'status': 'COMPLETED'},
                  separators=(',', ':')).encode()
stamp = str(int(time.time()))
signature = hmac.new(secret.encode(), stamp.encode() + b'.' + body, hashlib.sha256).hexdigest()
request = urllib.request.Request(args.base_url.rstrip('/') + '/webhooks/sandbox', data=body,
                                 headers={'Content-Type': 'application/json', 'X-Webhook-Timestamp': stamp,
                                          'X-Webhook-Signature': signature}, method='POST')
try:
    with urllib.request.urlopen(request, timeout=15) as response:
        print(f'Sandbox callback accepted (HTTP {response.status}). Check the wallet and receipt.')
except urllib.error.HTTPError as error:
    print(f'Sandbox callback rejected (HTTP {error.code}): {error.read().decode()}')
    raise SystemExit(1)
