/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.preparator.extension;

import de.rub.nds.tlsattacker.core.protocol.message.extension.HeartbeatExtensionMessage;
import de.rub.nds.tlsattacker.core.workflow.chooser.Chooser;

/**
 *
 * @author Robert Merget - robert.merget@rub.de
 */
public class HeartbeatExtensionPreparator extends ExtensionPreparator<HeartbeatExtensionMessage> {

    private HeartbeatExtensionMessage message;

    public HeartbeatExtensionPreparator(Chooser chooser, HeartbeatExtensionMessage message) {
        super(chooser, message);
        this.message = message;
    }

    @Override
    public void prepareExtensionContent() {
        message.setHeartbeatMode(chooser.getConfig().getHeartbeatMode().getArrayValue());
    }

}
