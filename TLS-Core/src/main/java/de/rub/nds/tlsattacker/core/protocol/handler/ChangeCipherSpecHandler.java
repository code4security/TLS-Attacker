/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.handler;

import de.rub.nds.tlsattacker.core.protocol.message.ChangeCipherSpecMessage;
import de.rub.nds.tlsattacker.core.protocol.parser.ChangeCipherSpecParser;
import de.rub.nds.tlsattacker.core.protocol.preparator.ChangeCipherSpecPreparator;
import de.rub.nds.tlsattacker.core.protocol.serializer.ChangeCipherSpecSerializer;
import de.rub.nds.tlsattacker.core.record.cipher.RecordCipher;
import de.rub.nds.tlsattacker.core.record.cipher.RecordCipherFactory;
import de.rub.nds.tlsattacker.core.workflow.TlsContext;
import de.rub.nds.tlsattacker.core.workflow.chooser.Chooser;
import de.rub.nds.tlsattacker.core.workflow.chooser.DefaultChooser;
import de.rub.nds.tlsattacker.transport.ConnectionEnd;

/**
 * @author Juraj Somorovsky <juraj.somorovsky@rub.de>
 * @author Philip Riese <philip.riese@rub.de>
 */
public class ChangeCipherSpecHandler extends ProtocolMessageHandler<ChangeCipherSpecMessage> {

    public ChangeCipherSpecHandler(TlsContext tlsContext) {
        super(tlsContext);
    }

    @Override
    public ChangeCipherSpecParser getParser(byte[] message, int pointer) {
        return new ChangeCipherSpecParser(pointer, message,
                new DefaultChooser(tlsContext, tlsContext.getConfig()).getLastRecordVersion());
    }

    @Override
    public ChangeCipherSpecPreparator getPreparator(ChangeCipherSpecMessage message) {
        return new ChangeCipherSpecPreparator(new DefaultChooser(tlsContext, tlsContext.getConfig()), message);
    }

    @Override
    public ChangeCipherSpecSerializer getSerializer(ChangeCipherSpecMessage message) {
        return new ChangeCipherSpecSerializer(message,
                new DefaultChooser(tlsContext, tlsContext.getConfig()).getSelectedProtocolVersion());
    }

    @Override
    protected void adjustTLSContext(ChangeCipherSpecMessage message) {
        if (tlsContext.getTalkingConnectionEnd() == ConnectionEnd.CLIENT) {
            setRecordCipher();
        }

        if (tlsContext.getTalkingConnectionEnd() != tlsContext.getConfig().getConnectionEnd()) {
            tlsContext.getRecordLayer().updateDecryptionCipher();
        }
    }

    private void setRecordCipher() {
        LOGGER.debug("Setting new Cipher in RecordLayer");
        RecordCipher recordCipher = RecordCipherFactory.getRecordCipher(tlsContext);
        tlsContext.getRecordLayer().setRecordCipher(recordCipher);
    }
}
