/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.parser.extension;

import de.rub.nds.tlsattacker.core.constants.ExtensionByteLength;
import de.rub.nds.tlsattacker.core.protocol.message.extension.ServerNameIndicationExtensionMessage;
import de.rub.nds.tlsattacker.core.protocol.message.extension.SNI.ServerNamePair;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author Robert Merget - robert.merget@rub.de
 */
public class ServerNameIndicationExtensionParser extends ExtensionParser<ServerNameIndicationExtensionMessage> {

    public ServerNameIndicationExtensionParser(int startposition, byte[] array) {
        super(startposition, array);
    }

    @Override
    public void parseExtensionMessageContent(ServerNameIndicationExtensionMessage msg) {
        msg.setServerNameListLength(parseIntField(ExtensionByteLength.SERVER_NAME_LIST_LENGTH));
        msg.setServerNameListBytes(parseByteArrayField(msg.getServerNameListLength().getValue()));
        int position = 0;
        List<ServerNamePair> pairList = new LinkedList<>();
        while (position < msg.getServerNameListLength().getValue()) {
            ServerNamePairParser parser = new ServerNamePairParser(position, msg.getServerNameListBytes().getValue());
            pairList.add(parser.parse());
        }
        msg.setServerNameList(pairList);
    }

    @Override
    protected ServerNameIndicationExtensionMessage createExtensionMessage() {
        return new ServerNameIndicationExtensionMessage();
    }
}