import test from 'node:test';
import assert from 'node:assert/strict';

async function setup(responses) {
  const calls = [];
  globalThis.window = new EventTarget();
  globalThis.fetch = async (url, options) => {
    calls.push({url, options});
    const next = responses.shift();
    assert.ok(next, 'Unexpected extra network request');
    return new Response(next.status === 204 ? null : JSON.stringify(next.body), {
      status: next.status ?? 200,
      headers: {'Content-Type':'application/json'},
    });
  };
  const api = await import(`../src/api.ts?case=${Math.random()}`);
  return { api, calls };
}

test('writes fetch CSRF first and send decimal values without converting them', async () => {
  const {api,calls}=await setup([{body:{headerName:'X-CSRF-TOKEN',token:'token-1'}},{body:{id:'saved'}}]);
  await api.post('/api/v1/rfqs',{quantity:'0.001'});
  assert.equal(calls[0].url,'/api/v1/csrf');
  assert.equal(calls[1].options.headers.get('X-CSRF-TOKEN'),'token-1');
  assert.equal(calls[1].options.credentials,'same-origin');
  assert.equal(JSON.parse(calls[1].options.body).quantity,'0.001');
});

test('login refreshes the CSRF token after session rotation', async () => {
  const {api,calls}=await setup([{body:{headerName:'X-CSRF-TOKEN',token:'old'}},{status:204},{body:{headerName:'X-CSRF-TOKEN',token:'new'}},{status:204}]);
  await api.login('operator','temporary-test-password');
  await api.post('/api/v1/action');
  assert.equal(calls[1].url,'/login');
  assert.equal(calls[1].options.headers.get('Content-Type'),'application/x-www-form-urlencoded');
  assert.equal(calls[3].options.headers.get('X-CSRF-TOKEN'),'new');
});

test('unauthorized responses notify the application and preserve status', async () => {
  const {api}=await setup([{status:401,body:{detail:'Session ended'}}]);
  let expired=false;
  window.addEventListener('session-expired',()=>{expired=true});
  await assert.rejects(api.request('/api/v1/auth/me'),error=>error.status===401&&error.message==='Session ended');
  assert.equal(expired,true);
});

test('directory selection loads every page', async () => {
  const {api,calls}=await setup([{body:{content:[{id:'first'}],totalPages:2}},{body:{content:[{id:'last'}],totalPages:2}}]);
  assert.deepEqual(await api.all('/api/v1/customers'),[{id:'first'},{id:'last'}]);
  assert.match(calls[1].url,/page=1&size=100/);
});

test('forbidden responses show the server detail and refresh CSRF', async () => {
  const {api,calls}=await setup([
    {body:{headerName:'X-CSRF-TOKEN',token:'stale'}},
    {status:403,body:{detail:'You cannot review your own request'}},
    {body:{headerName:'X-CSRF-TOKEN',token:'fresh'}},
    {body:{ok:true}},
  ]);
  await assert.rejects(
    api.post('/api/v1/action',{}),
    (error) => error.status === 403 && error.message === 'You cannot review your own request',
  );
  await api.post('/api/v1/action',{});
  assert.equal(calls[2].url,'/api/v1/csrf');
  assert.equal(calls[3].options.headers.get('X-CSRF-TOKEN'),'fresh');
});

test('conflict responses retain the server explanation', async () => {
  const {api}=await setup([{status:409,body:{detail:'Record changed concurrently. Reload it before retrying.'}}]);
  await assert.rejects(api.request('/api/v1/orders'),error=>error.status===409&&error.message.includes('Reload'));
});

test('a failed CSRF refresh does not hide the original permission error', async () => {
  const {api}=await setup([
    {body:{headerName:'X-CSRF-TOKEN',token:'old'}},
    {status:403,body:{detail:'Independent reviewer required'}},
  ]);
  const mockFetch=globalThis.fetch;
  let calls=0;
  globalThis.fetch=(...args)=>++calls===3 ? Promise.reject(new TypeError('Network unavailable')) : mockFetch(...args);
  await assert.rejects(api.post('/api/v1/review',{}),error=>error.status===403&&error.message==='Independent reviewer required');
});
