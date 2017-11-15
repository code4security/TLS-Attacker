/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.preparator.extension;

import de.rub.nds.modifiablevariable.util.ArrayConverter;
import de.rub.nds.tlsattacker.core.constants.ExtensionByteLength;
import de.rub.nds.tlsattacker.core.protocol.message.extension.PSK.PSKIdentity;
import de.rub.nds.tlsattacker.core.protocol.message.extension.PSK.PskSet;
import de.rub.nds.tlsattacker.core.protocol.preparator.Preparator;
import de.rub.nds.tlsattacker.core.workflow.chooser.Chooser;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 *
 * @author Marcel Maehren <marcel.maehren@rub.de>
 */
public class PSKIdentityPreparator extends Preparator<PSKIdentity> {
    
    private final PSKIdentity pskIdentity;
    private final PskSet pskSet;
    
    public PSKIdentityPreparator(Chooser chooser, PSKIdentity pskIdentity, PskSet pskSet) {
        super(chooser, pskIdentity);
        this.pskIdentity = pskIdentity;
        this.pskSet = pskSet;
    }
    @Override
    public void prepare() {
        LOGGER.debug("Preparing PSK identity");
        prepareIdentity();
        prepareObfuscatedTicketAge();
    }
    
    private void prepareIdentity()
    {
        pskIdentity.setIdentity(pskSet.getPreSharedKeyIdentity());
        pskIdentity.setIdentityLength(pskIdentity.getIdentity().getValue().length);
    }
    
    private void prepareObfuscatedTicketAge()
    {
        pskIdentity.setObfuscatedTicketAge(getObfuscatedTicketAge(pskSet.getTicketAgeAdd(), pskSet.getTicketAge()));
    }
    
    private byte[] getObfuscatedTicketAge(byte[] ticketAgeAdd, String ticketAge)
    {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        LocalDateTime ticketDate = LocalDateTime.parse(ticketAge, dateTimeFormatter);
        BigInteger difference = BigInteger.valueOf(Duration.between(ticketDate, LocalDateTime.now()).toMillis());
        BigInteger addValue = BigInteger.valueOf(ArrayConverter.bytesToLong(ticketAgeAdd));
        BigInteger mod = BigInteger.valueOf(2).pow(32);
        difference = difference.add(addValue);
        difference = difference.mod(mod);
        byte[] obfTicketAge = ArrayConverter.longToBytes(difference.longValue(), ExtensionByteLength.TICKET_AGE_LENGTH);
        
        LOGGER.debug("Calculated ObfuscatedTicketAge: " + ArrayConverter.bytesToHexString(obfTicketAge));
        return obfTicketAge;
    }
    
}
