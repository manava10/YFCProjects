import test from 'node:test';
import assert from 'node:assert/strict';
import { readForm, toIst, isPendingReview } from '../src/lib.ts';

function parse(entries) {
  const original = globalThis.FormData;
  globalThis.FormData = class { entries() { return entries[Symbol.iterator](); } };
  try { return readForm({}); } finally { globalThis.FormData = original; }
}

test('passwords keep exact whitespace while ordinary fields are trimmed', () => {
  const body = parse([['username','  operator  '],['password','  valid password  '],['newPassword',' next password '],['remarks','  site note  ']]);
  assert.deepEqual(body,{username:'operator',password:'  valid password  ',newPassword:' next password ',remarks:'site note'});
});

test('form parsing preserves decimal precision and converts only declared types', () => {
  assert.deepEqual(parse([['quantity','0.001'],['amount','999999999.99'],['active','false'],['revision','2'],['note','   ']]),{quantity:'0.001',amount:'999999999.99',active:false,revision:2});
});

test('operational timestamps retain timezone offsets and local entries use India time', () => {
  assert.equal(toIst('2026-10-21T12:30'),'2026-10-21T12:30:00+05:30');
  assert.equal(toIst('2026-10-21T07:00:00Z'),'2026-10-21T07:00:00Z');
});


test('dashboard review count includes submitted operating records', () => {
  const rows = [{id:'expense',status:'PENDING_VERIFICATION'},{id:'review',status:'PENDING'},{id:'done',status:'APPROVED'},{id:'rejected',status:'REJECTED'}];
  assert.equal(rows.filter(isPendingReview).length,2);
});
