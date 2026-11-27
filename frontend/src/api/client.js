export class ApiError extends Error {
  constructor(message, status, problem = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = problem.code;
    this.fieldErrors = problem.fieldErrors || {};
    this.requestId = problem.requestId || problem.traceId;
  }
}

const API_BASE = '/api/v1';
const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

async function handleResponse(response, asBlob = false) {
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new ApiError(
      problem.detail || 'The request could not be completed. Please try again.',
      response.status,
      problem,
    );
  }
  if (response.status === 204) return undefined;
  return asBlob ? response.blob() : response.json();
}

export async function request(
  path,
  { method = 'GET', body, headers = {}, signal, blob = false } = {},
) {
  const requestHeaders = { Accept: blob ? 'text/csv' : 'application/json', ...headers };
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json';

  // Request the server's CSRF token; the authentication cookie stays HttpOnly.
  if (UNSAFE_METHODS.has(method)) {
    const csrfResponse = await fetch(`${API_BASE}/auth/csrf`, {
      credentials: 'include',
      signal,
      cache: 'no-store',
    });
    const csrf = await handleResponse(csrfResponse);
    requestHeaders[csrf.headerName || 'X-XSRF-TOKEN'] = csrf.token;
  }

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers: requestHeaders,
      credentials: 'include',
      cache: 'no-store',
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new ApiError('Cannot reach WalletPay. Check your connection and try again.', 0);
  }
  if (response.status === 401 && !path.startsWith('/auth/')) {
    window.dispatchEvent(new Event('walletpay:session-expired'));
  }
  return handleResponse(response, blob);
}

export function filterQuery(filters = {}) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== '' && value !== undefined && value !== null && value !== 'ALL')
      params.set(key, String(value));
  }
  const query = params.toString();
  return query ? `?${query}` : '';
}

export async function downloadTransactions(filters) {
  const file = await request(`/transactions/export${filterQuery(filters)}`, { blob: true });
  const url = URL.createObjectURL(file);
  const link = document.createElement('a');
  link.href = url;
  link.download = `walletpay-transactions-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
