import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const errorRate       = new Rate('error_rate');
const projectDuration = new Trend('project_list_duration', true);
const taskDuration    = new Trend('task_update_duration', true);
const requestCount    = new Counter('total_requests');

export const options = {
  stages: [
    { duration: '30s', target: 5  },
    { duration: '1m',  target: 10 },
    { duration: '30s', target: 20 },
    { duration: '30s', target: 0  },
  ],
  thresholds: {
    http_req_duration:      ['p(95)<500'],
    http_req_failed:        ['rate<0.01'],
    error_rate:             ['rate<0.01'],
    project_list_duration:  ['p(95)<400'],
    task_update_duration:   ['p(95)<600'],
  },
};

const BASE_URL = 'http://localhost:8080';
const TOKEN    = 'JWT_TOKEN';

const HEADERS = {
  'Authorization': `Bearer ${TOKEN}`,
  'Content-Type': 'application/json',
};

export default function () {

  const listRes = http.get(`${BASE_URL}/projects`, { headers: HEADERS });
  requestCount.add(1);
  projectDuration.add(listRes.timings.duration);

  const listOk = check(listRes, {
    'GET /projects → 200':        (r) => r.status === 200,
    'GET /projects → has body':   (r) => r.body && r.body.length > 0,
    'GET /projects < 500ms':      (r) => r.timings.duration < 500,
  });
  errorRate.add(!listOk);

  sleep(0.5);

  const loginPayload = JSON.stringify({
    email: 'admin@acme.com',
    password: 'admin123',
  });
  const loginRes = http.post(`${BASE_URL}/auth/login`, loginPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  requestCount.add(1);

  const loginOk = check(loginRes, {
    'POST /auth/login → 200':    (r) => r.status === 200,
    'login returns token':       (r) => {
      try { return JSON.parse(r.body).token !== undefined; }
      catch { return false; }
    },
    'login < 500ms':             (r) => r.timings.duration < 500,
  });
  errorRate.add(!loginOk);

  sleep(0.5);

  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  requestCount.add(1);

  check(healthRes, {
    'GET /actuator/health → 200': (r) => r.status === 200,
    'health status UP':           (r) => {
      try { return JSON.parse(r.body).status === 'UP'; }
      catch { return false; }
    },
  });

  sleep(1);
}