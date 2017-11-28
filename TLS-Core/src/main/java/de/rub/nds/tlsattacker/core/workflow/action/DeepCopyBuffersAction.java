/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.workflow.action;

import de.rub.nds.tlsattacker.core.exceptions.WorkflowExecutionException;
import de.rub.nds.tlsattacker.core.protocol.message.ProtocolMessage;
import de.rub.nds.tlsattacker.core.record.AbstractRecord;
import de.rub.nds.tlsattacker.core.state.TlsContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;

public class DeepCopyBuffersAction extends CopyContextFieldAction {

    public DeepCopyBuffersAction() {

    }

    public DeepCopyBuffersAction(String srcConnectionAlias, String dstConnectionAlias) {
        super(srcConnectionAlias, dstConnectionAlias);
    }

    @Override
    protected void copyField(TlsContext src, TlsContext dst) {
        deepCopyRecords(src, dst);
        deepCopyMessages(src, dst);
    }

    @Override
    public boolean executedAsPlanned() {
        return isExecuted();
    }

    @Override
    public void reset() {
        setExecuted(false);
    }

    private void deepCopyRecords(TlsContext src, TlsContext dst) {
        LinkedList<AbstractRecord> recordBuffer = new LinkedList<>();
        ObjectOutputStream outStream;
        ObjectInputStream inStream;
        try {
            for (AbstractRecord record : src.getRecordBuffer()) {

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                outStream = new ObjectOutputStream(stream);
                outStream.writeObject(record);
                outStream.close();
                inStream = new ObjectInputStream(new ByteArrayInputStream(stream.toByteArray()));
                AbstractRecord recordCopy = (AbstractRecord) inStream.readObject();

                recordBuffer.add(recordCopy);
            }
        } catch (IOException | ClassNotFoundException ex) {
            LOGGER.error("Error while creating deep copy of recordBuffer");
            throw new WorkflowExecutionException(ex.toString());
        }

        dst.setRecordBuffer(recordBuffer);
    }

    private void deepCopyMessages(TlsContext src, TlsContext dst) {
        LinkedList<ProtocolMessage> messageBuffer = new LinkedList<>();
        ObjectOutputStream outStream;
        ObjectInputStream inStream;
        try {
            for (ProtocolMessage message : src.getMessageBuffer()) {

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                outStream = new ObjectOutputStream(stream);
                outStream.writeObject(message);
                outStream.close();
                inStream = new ObjectInputStream(new ByteArrayInputStream(stream.toByteArray()));
                ProtocolMessage messageCopy = (ProtocolMessage) inStream.readObject();

                messageBuffer.add(messageCopy);
            }
        } catch (IOException | ClassNotFoundException ex) {
            LOGGER.error("Error while creating deep copy of messageBuffer");
            throw new WorkflowExecutionException(ex.toString());
        }

        dst.setMessageBuffer(messageBuffer);
    }

}
