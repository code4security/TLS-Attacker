/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.workflow.action.executor;

import de.rub.nds.tlsattacker.core.constants.ProtocolMessageType;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.protocol.handler.ProtocolMessageHandler;
import de.rub.nds.tlsattacker.core.protocol.message.FinishedMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ProtocolMessage;
import de.rub.nds.tlsattacker.core.record.AbstractRecord;
import de.rub.nds.tlsattacker.core.record.cipher.RecordAEADCipher;
import de.rub.nds.tlsattacker.core.record.cipher.RecordCipher;
import de.rub.nds.tlsattacker.core.record.cipher.RecordCipherFactory;
import de.rub.nds.tlsattacker.core.record.layer.TlsRecordLayer;
import de.rub.nds.tlsattacker.core.state.TlsContext;
import de.rub.nds.tlsattacker.transport.ConnectionEndType;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Robert Merget <robert.merget@rub.de>
 */
public class SendMessageHelper {

    protected static final Logger LOGGER = LogManager.getLogger(SendMessageHelper.class.getName());

    public SendMessageHelper() {
    }

    public MessageActionResult sendMessages(List<ProtocolMessage> messages, List<AbstractRecord> records,
            TlsContext context) throws IOException {

        context.setTalkingConnectionEndType(context.getChooser().getConnectionEnd().getConnectionEndType());

        if (records == null) {
            LOGGER.trace("No Records Specified, creating emtpy list");
            records = new LinkedList<>();
        }

        int recordPosition = 0;
        ProtocolMessageType lastType = null;
        MessageBytesCollector messageBytesCollector = new MessageBytesCollector();
        for (ProtocolMessage message : messages) {
            if (message.getProtocolMessageType() != lastType && lastType != null
                    && context.getConfig().isFlushOnMessageTypeChange()) {
                recordPosition = flushBytesToRecords(messageBytesCollector, lastType, records, recordPosition, context);
                if (lastType == ProtocolMessageType.CHANGE_CIPHER_SPEC) {
                    context.getRecordLayer().updateEncryptionCipher();
                    context.setSequenceNumber(0);
                }
            }
            lastType = message.getProtocolMessageType();
            LOGGER.debug("Preparing " + message.toCompactString());
            byte[] protocolMessageBytes = handleProtocolMessage(message, context);
            if (message.isGoingToBeSent()) {
                messageBytesCollector.appendProtocolMessageBytes(protocolMessageBytes);
            }
            if (context.getConfig().isCreateIndividualRecords()) {
                recordPosition = flushBytesToRecords(messageBytesCollector, lastType, records, recordPosition, context);
            }
            if (context.getChooser().getSelectedProtocolVersion().isTLS13() && context.isUpdateKeys() == true) {
                LOGGER.debug("Setting new Cipher in RecordLayer");
                RecordCipher recordCipher = RecordCipherFactory.getRecordCipher(context);
                context.getRecordLayer().setRecordCipher(recordCipher);
                context.getRecordLayer().updateDecryptionCipher();
                context.getRecordLayer().updateEncryptionCipher();
                context.setSequenceNumber(0);
            }
        }
        if (lastType == ProtocolMessageType.CHANGE_CIPHER_SPEC) {
            context.getRecordLayer().updateEncryptionCipher();
            context.setSequenceNumber(0);
        }
        recordPosition = flushBytesToRecords(messageBytesCollector, lastType, records, recordPosition, context);
        sendData(messageBytesCollector, context);
        if (context.getConfig().isUseAllProvidedRecords() && recordPosition < records.size()) {
            int current = 0;
            for (AbstractRecord record : records) {
                if (current >= recordPosition) {
                    if (record.getMaxRecordLengthConfig() == null) {
                        record.setMaxRecordLengthConfig(context.getConfig().getDefaultMaxRecordData());
                    }
                    List<AbstractRecord> emptyRecords = new LinkedList<>();
                    emptyRecords.add(record);
                    messageBytesCollector.appendRecordBytes(context.getRecordLayer().prepareRecords(
                            messageBytesCollector.getProtocolMessageBytesStream(), record.getContentMessageType(),
                            emptyRecords));
                    sendData(messageBytesCollector, context);
                }
                current++;
            }
        }
        if(context.getConnectionEnd().getConnectionEndType() == ConnectionEndType.SERVER && context.getSelectedProtocolVersion().isTLS13())
        {
            for(ProtocolMessage message : messages)
            {
                if(message instanceof FinishedMessage && context.getConnectionEnd().getConnectionEndType() == ConnectionEndType.SERVER)
                {
                    //Switch RecordLayer secrets to prepare for EndOfEarlyData
                    adjustRecordCipherAfterServerFinished(context);
                    break;
                }
            }
        }
        return new MessageActionResult(records, messages);
    }

    private int flushBytesToRecords(MessageBytesCollector collector, ProtocolMessageType type,
            List<AbstractRecord> records, int recordPosition, TlsContext context) {
        int length = collector.getProtocolMessageBytesStream().length;
        List<AbstractRecord> toFillList = getEnoughRecords(length, recordPosition, records, context);
        collector.appendRecordBytes(context.getRecordLayer().prepareRecords(collector.getProtocolMessageBytesStream(),
                type, toFillList));
        collector.flushProtocolMessageBytes();
        return recordPosition + toFillList.size();
    }

    private List<AbstractRecord> getEnoughRecords(int length, int position, List<AbstractRecord> records,
            TlsContext context) {
        List<AbstractRecord> toFillList = new LinkedList<>();
        int recordLength = 0;
        while (recordLength < length) {
            if (position >= records.size()) {
                if (context.getConfig().isCreateRecordsDynamically()) {
                    LOGGER.trace("Creating new Record");
                    records.add(context.getRecordLayer().getFreshRecord());
                } else {
                    return toFillList;
                }
            }
            AbstractRecord record = records.get(position);
            toFillList.add(record);
            if (record.getMaxRecordLengthConfig() == null) {
                record.setMaxRecordLengthConfig(context.getConfig().getDefaultMaxRecordData());
            }
            recordLength += record.getMaxRecordLengthConfig();
            position++;
        }
        return toFillList;
    }

    /**
     * Sends all messageBytes in the MessageByteCollector with the specified
     * TransportHandler
     *
     * @param handler
     *            TransportHandler to send the Data with
     * @param messageBytesCollector
     *            MessageBytes to send
     * @throws IOException
     *             Thrown if something goes wrong while sending
     */
    private void sendData(MessageBytesCollector collector, TlsContext context) throws IOException {
        context.getTransportHandler().sendData(collector.getRecordBytes());
        collector.flushRecordBytes();
    }

    private byte[] handleProtocolMessage(ProtocolMessage message, TlsContext context) {
        ProtocolMessageHandler handler = message.getHandler(context);
        byte[] protocolMessageBytes = handler.prepareMessage(message);
        return protocolMessageBytes;
    }
    
        
    private void adjustRecordCipherAfterServerFinished(TlsContext context)
    {
        LOGGER.debug("Adjusting recordCipher after encrypting Hanshake messages");   
        context.setUseEarlyTrafficSecret(true);
        if(context.getRecordLayer() instanceof TlsRecordLayer && ((TlsRecordLayer)context.getRecordLayer()).getRecordCipher() instanceof RecordAEADCipher)
        {
            context.setStoredSequenceNumberEnc(((RecordAEADCipher)((TlsRecordLayer)context.getRecordLayer()).getRecordCipher()).getSequenceNumberEnc());
        }
        RecordCipher recordCipher = RecordCipherFactory.getRecordCipher(context, context.getEarlyDataCipherSuite());
        context.getRecordLayer().setRecordCipher(recordCipher);
        context.getRecordLayer().updateDecryptionCipher();
        context.getRecordLayer().updateEncryptionCipher();
        
        //Restore the correct SequenceNumber
        ((RecordAEADCipher)recordCipher).setSequenceNumberDec(1);
    }
}
