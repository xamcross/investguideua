// Unit tests for the exact decimal -> minor-unit conversion (features 009, 011). Run: `node --test`.
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

// ---- feature 011: metals price strings with a (possibly non-ASCII) space thousands separator ----

test('strips an ASCII space thousands separator (metals)', () => {
  assert.equal(toMinorUnits('6 780.00'), 678000);
  assert.equal(toMinorUnits('8 880.00'), 888000);
});

test('strips non-breaking / thin / narrow / figure space separators (metals)', () => {
  // Build separators from explicit code points so the source stays unambiguous ASCII and the test
  // genuinely exercises non-ASCII whitespace (not a plain space that merely looks identical).
  const withSep = (code) => '6' + String.fromCharCode(code) + '780.00';
  assert.equal(toMinorUnits(withSep(0x00a0)), 678000); // non-breaking space
  assert.equal(toMinorUnits(withSep(0x2009)), 678000); // thin space
  assert.equal(toMinorUnits(withSep(0x202f)), 678000); // narrow no-break space
  assert.equal(toMinorUnits(withSep(0x2007)), 678000); // figure space
});

test('small metals values convert exactly', () => {
  assert.equal(toMinorUnits('100.75'), 10075);
  assert.equal(toMinorUnits('62.40'), 6240);
});

test('comma is still rejected (not whitespace)', () => {
  assert.throws(() => toMinorUnits('6,780.00'));
  assert.throws(() => toMinorUnits('1,076.58'));
});
