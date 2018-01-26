package com.borunovv.contract;

public class PreconditionException extends RuntimeException {
    public PreconditionException(String message) {
        super(message);
    }
}
