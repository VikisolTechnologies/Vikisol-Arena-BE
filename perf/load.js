// k6 load test: 50 concurrent signed-in users on feed, discover, nearby, jobs and messages.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const BASE = __ENV.BASE || 'http://localhost:8081/api/v1';
const users = new SharedArray('users', () => JSON.parse(open('/tmp/arena-pgload/seed/tokens.json')));
const CITIES = [[17.40, 78.45], [12.97, 77.59], [19.07, 72.88], [28.61, 77.21], [13.08, 80.27]];
const TERMS = ['cricket', 'yoga', 'design', 'trek', 'chess', 'music', 'engineer'];

export const options = {
  scenarios: {
    app: { executor: 'ramping-vus', startVUs: 0, stages: [
      { duration: '20s', target: Number(__ENV.VUS || 50) }, { duration: __ENV.HOLD || '2m', target: Number(__ENV.VUS || 50) }, { duration: '10s', target: 0 }] },
  },
  thresholds: {
    'http_req_duration{name:feed}': ['p(95)<300'],
    'http_req_duration{name:discover_trending}': ['p(95)<300'],
    'http_req_duration{name:discover_search}': ['p(95)<300'],
    'http_req_duration{name:nearby}': ['p(95)<300'],
    'http_req_duration{name:jobs_list}': ['p(95)<300'],
    'http_req_duration{name:job_detail}': ['p(95)<300'],
    'http_req_duration{name:conversations}': ['p(95)<300'],
    'http_req_duration{name:messages}': ['p(95)<300'],
    'http_req_failed': ['rate<0.01'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function get(path, name, token) {
  const r = http.get(BASE + path, { headers: { Authorization: 'Bearer ' + token }, tags: { name } });
  check(r, { [name + ' 200']: (x) => x.status === 200 });
  if (r.status !== 200 && __ENV.DEBUG) console.log('FAIL ' + name + ' ' + r.status + ' ' + String(r.body).substring(0, 160));
  return r;
}

export default function () {
  const u = users[(__VU - 1) % users.length];
  const [lat, lng] = CITIES[Math.floor(Math.random() * CITIES.length)];
  const page = Math.floor(Math.random() * 3);
  get(`/feed?tab=for_you&page=${page}&size=20`, 'feed', u.token);
  get(`/posts/trending?page=0&size=20`, 'discover_trending', u.token);
  get(`/search?q=${TERMS[Math.floor(Math.random() * TERMS.length)]}&type=all&limit=20`, 'discover_search', u.token);
  get(`/posts/nearby?lat=${lat + (Math.random() - 0.5) * 0.2}&lng=${lng + (Math.random() - 0.5) * 0.2}&radiusKm=5`, 'nearby', u.token);
  const jobs = get(`/jobs?page=${page}&size=20`, 'jobs_list', u.token);
  const list = jobs.json('data.content') || [];
  if (list.length) get(`/jobs/${list[Math.floor(Math.random() * list.length)].id}`, 'job_detail', u.token);
  const convs = get(`/messages/conversations`, 'conversations', u.token).json('data') || [];
  if (convs.length) get(`/messages/conversations/${convs[Math.floor(Math.random() * convs.length)].id}/messages`, 'messages', u.token);
  sleep(0.5 + Math.random());
}
