// Unit tests for the metals batch validation (feature 011). Run: `node --test`.
//
// The headline rule that does NOT exist in 009: a batch must contain BOTH gold and silver, even when
// it clears the record-count floor (FR-015, spec Edge Cases "one metal entirely absent").
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateMetalBatch, DEFAULT_METALS_MIN_RECORDS } from '../lib/validate.mjs';

function record(metal, weightKey) {
  return {
    metal,
    rateGroup: 'one',
    weightKey,
    weightGrams: Number(weightKey),
    currency: 'UAH',
    quotationDate: '2026-06-08',
    purchaseRateMinor: 678000,
    saleRateMinor: 888000,
  };
}

/** Build `count` records all of one metal (distinct weightKeys), or split across both metals. */
function batch(metal, count) {
  return Array.from({ length: count }, (_, i) => record(metal, String(i + 1)));
}

function bothMetalsBatch(perMetal) {
  return [...batch('GOLD', perMetal), ...batch('SILVER', perMetal)];
}

test('valid both-metals batch passes', () => {
  const records = bothMetalsBatch(15); // 30 records, both metals
  assert.equal(validateMetalBatch(records).length, 30);
});

test('batch missing a metal is rejected even above the record floor', () => {
  const goldOnly = batch('GOLD', DEFAULT_METALS_MIN_RECORDS + 5); // clears the floor, but silver absent
  assert.ok(goldOnly.length >= DEFAULT_METALS_MIN_RECORDS);
  assert.throws(() => validateMetalBatch(goldOnly), /missing a metal/);
});

test('batch below the record floor is rejected', () => {
  const tiny = [record('GOLD', '1'), record('SILVER', '10')];
  assert.throws(() => validateMetalBatch(tiny), /minimum-record floor/);
});

test('empty / non-array is rejected', () => {
  assert.throws(() => validateMetalBatch([]), /empty/);
  assert.throws(() => validateMetalBatch(null), /not an array/);
});

test('a malformed record is rejected', () => {
  const records = bothMetalsBatch(15);
  records[0].saleRateMinor = -1; // negative rate
  assert.throws(() => validateMetalBatch(records), /saleRateMinor/);

  const records2 = bothMetalsBatch(15);
  records2[1].metal = 'PLATINUM';
  assert.throws(() => validateMetalBatch(records2), /metal invalid/);
});
