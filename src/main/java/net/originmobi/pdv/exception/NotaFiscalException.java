package net.originmobi.pdv.exception;

public class NotaFiscalException extends RuntimeException {
    public NotaFiscalException(String message) {
        super(message);
    }

    public NotaFiscalException(String message, Throwable cause) {
        super(message, cause);
    }
}
