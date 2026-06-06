package com.investguide.bonds.dto;

/**
 * Outcome of an ingest batch (feature 009): how many records were accepted (upserted) vs rejected
 * for failing validation.
 */
public record IngestResult(int accepted, int rejected) {
}
