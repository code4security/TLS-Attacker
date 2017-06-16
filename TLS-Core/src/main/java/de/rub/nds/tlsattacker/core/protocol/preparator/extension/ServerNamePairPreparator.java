/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.preparator.extension;

import de.rub.nds.tlsattacker.core.protocol.message.extension.SNI.ServerNamePair;
import de.rub.nds.tlsattacker.core.protocol.preparator.Preparator;
import de.rub.nds.tlsattacker.core.workflow.chooser.Chooser;

/**
 *
 * @author Robert Merget - robert.merget@rub.de
 */
public class ServerNamePairPreparator extends Preparator<ServerNamePair> {

    private final ServerNamePair pair;

    public ServerNamePairPreparator(Chooser chooser, ServerNamePair pair) {
        super(chooser, pair);
        this.pair = pair;
    }

    @Override
    public void prepare() {
        pair.setServerName(pair.getServerNameConfig());
        pair.setServerNameType(pair.getServerNameTypeConfig());
        pair.setServerNameLength(pair.getServerName().getValue().length);
    }

}
