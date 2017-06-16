/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.handler;

import de.rub.nds.tlsattacker.core.protocol.message.DHEServerKeyExchangeMessage;
import de.rub.nds.tlsattacker.core.protocol.parser.DHEServerKeyExchangeParser;
import de.rub.nds.tlsattacker.core.protocol.preparator.DHEServerKeyExchangePreparator;
import de.rub.nds.tlsattacker.core.protocol.serializer.DHEServerKeyExchangeSerializer;
import de.rub.nds.tlsattacker.core.workflow.TlsContext;
import de.rub.nds.tlsattacker.core.workflow.chooser.DefaultChooser;
import java.math.BigInteger;

/**
 * @author Juraj Somorovsky <juraj.somorovsky@rub.de>
 * @author Philip Riese <philip.riese@rub.de>
 */
public class DHEServerKeyExchangeHandler extends ServerKeyExchangeHandler<DHEServerKeyExchangeMessage> {

    public DHEServerKeyExchangeHandler(TlsContext tlsContext) {
        super(tlsContext);
    }

    @Override
    public DHEServerKeyExchangeParser getParser(byte[] message, int pointer) {
        return new DHEServerKeyExchangeParser(pointer, message,
                new DefaultChooser(tlsContext, tlsContext.getConfig()).getLastRecordVersion());
    }

    @Override
    public DHEServerKeyExchangePreparator getPreparator(DHEServerKeyExchangeMessage message) {
        return new DHEServerKeyExchangePreparator(new DefaultChooser(tlsContext, tlsContext.getConfig()), message);
    }

    @Override
    public DHEServerKeyExchangeSerializer getSerializer(DHEServerKeyExchangeMessage message) {
        return new DHEServerKeyExchangeSerializer(message,
                new DefaultChooser(tlsContext, tlsContext.getConfig()).getSelectedProtocolVersion());
    }

    @Override
    protected void adjustTLSContext(DHEServerKeyExchangeMessage message) {
        adjustDhGenerator(message);
        adjustDhModulus(message);
        adjustServerPublicKey(message);
        if (message.getComputations() != null && message.getComputations().getPrivateKey() != null) {
            adjustServerPrivateKey(message);
        }
    }

    /**
     * TODO Preparators should never change Context fields
     *
     * @param context
     */
    private void adjustDhGenerator(DHEServerKeyExchangeMessage message) {
        tlsContext.setDhGenerator(new BigInteger(1, message.getGenerator().getValue()));
        LOGGER.debug("Dh Generator: " + tlsContext.getDhGenerator());
    }

    private void adjustDhModulus(DHEServerKeyExchangeMessage message) {
        tlsContext.setDhModulus(new BigInteger(1, message.getModulus().getValue()));
        LOGGER.debug("Dh Modulus: " + tlsContext.getDhModulus());
    }

    private void adjustServerPublicKey(DHEServerKeyExchangeMessage message) {
        tlsContext.setServerDhPublicKey(new BigInteger(1, message.getPublicKey().getValue()));
        LOGGER.debug("Server PublicKey: " + tlsContext.getServerDhPublicKey());
    }

    private void adjustServerPrivateKey(DHEServerKeyExchangeMessage message) {
        tlsContext.setServerDhPrivateKey(message.getComputations().getPrivateKey().getValue());
        LOGGER.debug("Server PrivateKey: " + tlsContext.getServerDhPrivateKey());
    }
}
