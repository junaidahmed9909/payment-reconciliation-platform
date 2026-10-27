// k6 load test for the real-time API.
// Install k6 (https://k6.io), start the stack, then run:
//   k6 run loadtest/stream-load.js
//   k6 run -e BASE_URL=http://localhost:8080 -e RATE=500 loadtest/stream-load.js
// Each iteration sends one bank and one ledger transaction with the same reference.
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = Number(__ENV.RATE || 200); // pairs per second

export const options = {
  scenarios: {
    pairs: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: __ENV.DURATION || '1m',
      preAllocatedVUs: 50,
      maxVUs: 500,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<200'],
  },
};

const headers = { 'Content-Type': 'application/json' };

function txn(source, ref, amount) {
  return JSON.stringify({
    source, reference: ref, accountId: 'ACC' + (1000 + (__ITER % 50)),
    amount, currency: 'USD', valueDate: '2026-03-01', description: 'k6',
  });
}

export default function () {
  const ref = `K6-${__VU}-${__ITER}-${Date.now()}`;
  const amount = (Math.floor(Math.random() * 100000) / 100).toFixed(2);
  const ok = (r) => r.status === 200 || r.status === 202;
  check(http.post(`${BASE_URL}/api/transactions`, txn('BANK', ref, amount), { headers }), { 'bank accepted': ok });
  check(http.post(`${BASE_URL}/api/transactions`, txn('LEDGER', ref, amount), { headers }), { 'ledger accepted': ok });
}
