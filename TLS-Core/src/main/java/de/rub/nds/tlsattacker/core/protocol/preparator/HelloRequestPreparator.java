/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.preparator;

import de.rub.nds.tlsattacker.core.protocol.message.HelloRequestMessage;
import de.rub.nds.tlsattacker.core.workflow.chooser.Chooser;

public class HelloRequestPreparator extends HandshakeMessagePreparator<HelloRequestMessage> {

    public HelloRequestPreparator(Chooser chooser, HelloRequestMessage message) {
        super(chooser, message);
    }

    @Override
    public void prepareHandshakeMessageContents() {
        LOGGER.debug("Preparing HelloRequestMessage");
    }

}
