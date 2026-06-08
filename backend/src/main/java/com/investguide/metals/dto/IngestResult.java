package com.investguide.metals.dto;

/**
 * Outcome of a metals ingest batch (feature 011): how many records were accepted (upserted) vs
 * rejected for failing validation.
 */
public record IngestResult(int accepted, int rejected) {
}
