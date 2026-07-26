package com.investguide.news;

/** A feed could not be fetched or parsed (feature 016). Caught per feed - never fatal. */
public class NewsFeedException extends RuntimeException {
    public NewsFeedException(String message, Throwable cause) {
        super(message, cause);
    }
}
