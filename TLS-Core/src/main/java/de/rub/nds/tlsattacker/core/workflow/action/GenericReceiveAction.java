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
import de.rub.nds.tlsattacker.core.state.State;
import static de.rub.nds.tlsattacker.core.workflow.action.TlsAction.LOGGER;
import de.rub.nds.tlsattacker.core.workflow.action.executor.MessageActionResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class GenericReceiveAction extends MessageAction implements ReceivingAction {

    public GenericReceiveAction() {
        super();
    }

    public GenericReceiveAction(List<ProtocolMessage> messages) {
        super(messages);
    }

    public GenericReceiveAction(ProtocolMessage... messages) {
        this(new ArrayList<>(Arrays.asList(messages)));
    }

    public GenericReceiveAction(String connectionAlias) {
        super(connectionAlias);
    }

    public GenericReceiveAction(String connectionAlias, List<ProtocolMessage> messages) {
        super(connectionAlias, messages);
    }

    public GenericReceiveAction(String connectionAlias, ProtocolMessage... messages) {
        super(connectionAlias, new ArrayList<>(Arrays.asList(messages)));
    }

    @Override
    public void execute(State state) {
        if (isExecuted()) {
            throw new WorkflowExecutionException("Action already executed!");
        }
        LOGGER.debug("Receiving Messages...");
        MessageActionResult result = receiveMessageHelper.receiveMessages(state.getTlsContext(getConnectionAlias()));
        records.addAll(result.getRecordList());
        messages.addAll(result.getMessageList());
        setExecuted(true);
        String received = getReadableString(messages);
        LOGGER.info("Received Messages:" + received);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Receive Action:\n");
        sb.append("\tActual:");
        for (ProtocolMessage message : messages) {
            sb.append(message.toCompactString());
            sb.append(", ");
        }
        return sb.toString();
    }

    @Override
    public boolean executedAsPlanned() {
        return isExecuted();
    }

    @Override
    public void reset() {
        messages = new LinkedList<>();
        records = new LinkedList<>();
        setExecuted(Boolean.FALSE);
    }

    @Override
    public List<ProtocolMessage> getReceivedMessages() {
        return messages;
    }

    @Override
    public List<AbstractRecord> getReceivedRecords() {
        return records;
    }
}
