/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.handler.extension;

import de.rub.nds.tlsattacker.core.constants.ECPointFormat;
import de.rub.nds.tlsattacker.core.protocol.message.extension.ECPointFormatExtensionMessage;
import de.rub.nds.tlsattacker.core.protocol.parser.extension.ECPointFormatExtensionParser;
import de.rub.nds.tlsattacker.core.protocol.parser.extension.ExtensionParser;
import de.rub.nds.tlsattacker.core.protocol.preparator.extension.ECPointFormatExtensionPreparator;
import de.rub.nds.tlsattacker.core.protocol.preparator.extension.ExtensionPreparator;
import de.rub.nds.tlsattacker.core.protocol.serializer.extension.ECPointFormatExtensionSerializer;
import de.rub.nds.tlsattacker.core.protocol.serializer.extension.ExtensionSerializer;
import de.rub.nds.tlsattacker.core.workflow.TlsContext;
import de.rub.nds.tlsattacker.core.workflow.chooser.DefaultChooser;
import de.rub.nds.tlsattacker.transport.ConnectionEnd;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Juraj Somorovsky <juraj.somorovsky@rub.de>
 */
public class ECPointFormatExtensionHandler extends ExtensionHandler<ECPointFormatExtensionMessage> {

    public ECPointFormatExtensionHandler(TlsContext context) {
        super(context);
    }

    @Override
    public void adjustTLSContext(ECPointFormatExtensionMessage message) {
        List<ECPointFormat> formatList = new LinkedList<>();
        byte[] pointFormats = message.getPointFormats().getValue();
        for (byte b : pointFormats) {
            ECPointFormat format = ECPointFormat.getECPointFormat(b);
            if (format != null) {
                formatList.add(format);
            } else {
                LOGGER.warn("Unknown ECPointFormat:" + b);
            }
        }
        if (context.getTalkingConnectionEnd() == ConnectionEnd.CLIENT) {
            context.setClientPointFormatsList(formatList);
        } else {
            context.setServerPointFormatsList(formatList);
        }

    }

    @Override
    public ExtensionParser getParser(byte[] message, int pointer) {
        return new ECPointFormatExtensionParser(pointer, message);
    }

    @Override
    public ExtensionPreparator getPreparator(ECPointFormatExtensionMessage message) {
        return new ECPointFormatExtensionPreparator(new DefaultChooser(context, context.getConfig()), message);
    }

    @Override
    public ExtensionSerializer getSerializer(ECPointFormatExtensionMessage message) {
        return new ECPointFormatExtensionSerializer(message);
    }

}
