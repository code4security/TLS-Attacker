/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2016 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.tls.exceptions;

/**
 *
 * @author Robert Merget - robert.merget@rub.de
 */
public class PreparationException extends RuntimeException {

    public PreparationException() {
    }

    public PreparationException(String message) {
        super(message);
    }

    public PreparationException(String message, Throwable cause) {
        super(message, cause);
    }
}
