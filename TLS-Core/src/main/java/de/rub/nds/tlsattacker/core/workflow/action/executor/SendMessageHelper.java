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
import de.rub.nds.tlsattacker.core.protocol.handler.ProtocolMessageHandler;
import de.rub.nds.tlsattacker.core.protocol.message.ProtocolMessage;
import de.rub.nds.tlsattacker.core.record.AbstractRecord;
import de.rub.nds.tlsattacker.core.state.TlsContext;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SendMessageHelper {

    protected static final Logger LOGGER = LogManager.getLogger(SendMessageHelper.class.getName());

    public SendMessageHelper() {
    }

    public MessageActionResult sendMessages(List<ProtocolMessage> messages, List<AbstractRecord> records,
            TlsContext context) throws IOException {
        return sendMessages(messages, records, context, true);
    }

    public MessageActionResult sendMessages(List<ProtocolMessage> messages, List<AbstractRecord> records,
            TlsContext context, boolean prepareMessages) throws IOException {

        context.setTalkingConnectionEndType(context.getChooser().getConnectionEndType());

        if (records == null) {
            LOGGER.trace("No Records Specified, creating emtpy list");
            records = new LinkedList<>();
        }

        int recordPosition = 0;
        ProtocolMessageType lastType = null;
        ProtocolMessage lastMessage = null;
        MessageBytesCollector messageBytesCollector = new MessageBytesCollector();
        for (ProtocolMessage message : messages) {
            if (message.getProtocolMessageType() != lastType && lastMessage != null
                    && context.getConfig().isFlushOnMessageTypeChange()) {
                recordPosition = flushBytesToRecords(messageBytesCollector, lastType, records, recordPosition, context);
                lastMessage.getHandler(context).adjustTlsContextAfterSerialize(lastMessage);
                lastMessage = null;
            }
            lastMessage = message;
            lastType = message.getProtocolMessageType();
            byte[] protocolMessageBytes;
            if (prepareMessages) {
                LOGGER.debug("Preparing " + message.toCompactString());
                protocolMessageBytes = handleProtocolMessage(message, context);
            } else {
                protocolMessageBytes = handleProtocolMessageWithoutPrepare(message, context);
            }
            if (message.isGoingToBeSent()) {
                messageBytesCollector.appendProtocolMessageBytes(protocolMessageBytes);
            } else {
                LOGGER.debug("Not adding message bytes for " + message.toCompactString() + " - goingToBeSent is false!");
            }
            if (context.getConfig().isCreateIndividualRecords()) {
                recordPosition = flushBytesToRecords(messageBytesCollector, lastType, records, recordPosition, context);
                message.getHandler(context).adjustTlsContextAfterSerialize(message);
                lastMessage = null;
            }
        }
        recordPosition = flushBytesToRecords(messageBytesCollector, lastType, records, recordPosition, context);
        if (lastMessage != null) {
            lastMessage.getHandler(context).adjustTlsContextAfterSerialize(lastMessage);
        }
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
        return new MessageActionResult(records, messages);
    }

    public void sendRecords(List<AbstractRecord> records, TlsContext context) throws IOException {

        context.setTalkingConnectionEndType(context.getChooser().getConnectionEndType());

        if (records == null) {
            LOGGER.debug("No records specified, nothing to send");
            return;
        }

        MessageBytesCollector messageBytesCollector = new MessageBytesCollector();
        sendData(messageBytesCollector, context);

        for (AbstractRecord record : records) {
            messageBytesCollector.appendRecordBytes(record.getRecordSerializer().serialize());
        }
        LOGGER.debug("Sending " + records.size() + "records");
        sendData(messageBytesCollector, context);
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

    private byte[] handleProtocolMessageWithoutPrepare(ProtocolMessage message, TlsContext context) {
        ProtocolMessageHandler handler = message.getHandler(context);
        byte[] protocolMessageBytes = handler.prepareMessage(message, false);
        return protocolMessageBytes;
    }

    private byte[] handleProtocolMessage(ProtocolMessage message, TlsContext context) {
        ProtocolMessageHandler handler = message.getHandler(context);
        byte[] protocolMessageBytes = handler.prepareMessage(message);
        return protocolMessageBytes;
    }
}
