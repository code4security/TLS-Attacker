/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.parser;

import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.constants.ssl.SSL2ByteLength;
import de.rub.nds.tlsattacker.core.protocol.message.SSL2HandshakeMessage;

public abstract class SSL2HandshakeMessageParser<T extends SSL2HandshakeMessage> extends ProtocolMessageParser<T> {

    public SSL2HandshakeMessageParser(int pointer, byte[] array, ProtocolVersion version) {
        super(pointer, array, version);
    }

    /**
     * Reads the next bytes as the MessageLength and writes them in the message
     *
     * @param message
     *            Message to write in
     */
    protected void parseMessageLength(T message) {
        // The "wonderful" SSL2 message length field:
        // 2-byte header: RECORD-LENGTH = ((byte[0] & 0x7f) << 8)) | byte[1];
        // 3-byte header: RECORD-LENGTH = ((byte[0] & 0x3f) << 8)) | byte[1];
        // If most significant bit on first byte is set: 2-byte header.
        // O/w, 3-byte header.
        byte[] first2Bytes = parseByteArrayField(2);
        int mask;
        if ((first2Bytes[0] & 0x80) == 0) {
            mask = 0x3f;
        } else {
            mask = 0x7f;
        }
        int len = ((first2Bytes[0] & mask) << 8) | (first2Bytes[1] & 0xFF);
        message.setMessageLength(len);
        LOGGER.debug("MessageLength: " + message.getMessageLength().getValue());
    }

    /**
     * Reads the next bytes as the Type and writes them in the message
     *
     * @param msg
     *            Message to write in
     */
    protected void parseType(T msg) {
        msg.setType(parseByteField(SSL2ByteLength.MESSAGE_TYPE));
        LOGGER.debug("Type: " + msg.getType().getValue());
    }

}
