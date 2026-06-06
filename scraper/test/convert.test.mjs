// Unit tests for the exact decimal -> minor-unit conversion (feature 009). Run: `node --test`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { toMinorUnits } from '../lib/convert.mjs';

test('converts the canonical example exactly', () => {
  assert.equal(toMinorUnits('1076.58'), 107658);
  assert.equal(toMinorUnits(1076.58), 107658);
});

test('whole numbers get two zero minor digits', () => {
  assert.equal(toMinorUnits('1000'), 100000);
  assert.equal(toMinorUnits(1000), 100000);
  assert.equal(toMinorUnits('0'), 0);
});

test('one fractional digit pads to two', () => {
  assert.equal(toMinorUnits('1000.5'), 100050);
  assert.equal(toMinorUnits('0.7'), 70);
});

test('avoids floating point drift', () => {
  // 1076.58 * 100 is 107657.99999999999 in IEEE-754; string math must give 107658.
  assert.equal(toMinorUnits('1076.58'), 107658);
  assert.equal(toMinorUnits('19.99'), 1999);
});

test('rounds half up beyond two fraction digits', () => {
  assert.equal(toMinorUnits('1.005'), 101); // .005 -> round up
  assert.equal(toMinorUnits('1.004'), 100); // .004 -> round down
  assert.equal(toMinorUnits('1076.585'), 107659);
  assert.equal(toMinorUnits('1076.584'), 107658);
});

test('rejects missing / empty / non-numeric (never silently 0)', () => {
  assert.throws(() => toMinorUnits(null));
  assert.throws(() => toMinorUnits(undefined));
  assert.throws(() => toMinorUnits(''));
  assert.throws(() => toMinorUnits('abc'));
  assert.throws(() => toMinorUnits('1,076.58'));
});
