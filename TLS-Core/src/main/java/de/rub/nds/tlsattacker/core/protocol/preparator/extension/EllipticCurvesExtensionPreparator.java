/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.preparator.extension;

import de.rub.nds.tlsattacker.core.constants.NamedCurve;
import de.rub.nds.tlsattacker.core.exceptions.PreparationException;
import de.rub.nds.tlsattacker.core.protocol.message.extension.EllipticCurvesExtensionMessage;
import de.rub.nds.tlsattacker.core.workflow.chooser.Chooser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 *
 * @author Robert Merget - robert.merget@rub.de
 */
public class EllipticCurvesExtensionPreparator extends ExtensionPreparator<EllipticCurvesExtensionMessage> {

    private EllipticCurvesExtensionMessage message;

    public EllipticCurvesExtensionPreparator(Chooser chooser, EllipticCurvesExtensionMessage message) {
        super(chooser, message);
        this.message = message;
    }

    @Override
    public void prepareExtensionContent() {
        prepareEllipticCurves();
        message.setSupportedCurvesLength(message.getSupportedCurves().getValue().length);
    }

    private void prepareEllipticCurves() {
        message.setSupportedCurves(createEllipticCurveArray());
    }

    private byte[] createEllipticCurveArray() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (NamedCurve curve : chooser.getConfig().getNamedCurves()) {
            try {
                stream.write(curve.getValue());
            } catch (IOException ex) {
                throw new PreparationException("Could not write NamedCurve to byte[]", ex);
            }
        }
        return stream.toByteArray();
    }
}
