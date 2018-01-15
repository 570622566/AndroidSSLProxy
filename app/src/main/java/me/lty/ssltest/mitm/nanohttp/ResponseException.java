package me.lty.ssltest.mitm.nanohttp;

class ResponseException extends Exception {

        private static final long serialVersionUID = 6569838532917408380L;

        private final Response.Status status;

        public ResponseException(Response.Status status, String message) {
            super(message);
            this.status = status;
        }

        public ResponseException(Response.Status status, String message, Exception e) {
            super(message, e);
            this.status = status;
        }

        public Response.Status getStatus() {
            return this.status;
        }
    }