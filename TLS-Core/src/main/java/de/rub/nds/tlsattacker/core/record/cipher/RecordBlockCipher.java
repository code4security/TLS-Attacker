/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.record.cipher;

import de.rub.nds.modifiablevariable.util.ArrayConverter;
import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.constants.CipherAlgorithm;
import de.rub.nds.tlsattacker.core.constants.MacAlgorithm;
import de.rub.nds.tlsattacker.core.crypto.cipher.CipherWrapper;
import de.rub.nds.tlsattacker.core.exceptions.CryptoException;
import de.rub.nds.tlsattacker.core.record.cipher.cryptohelper.DecryptionRequest;
import de.rub.nds.tlsattacker.core.record.cipher.cryptohelper.DecryptionResult;
import de.rub.nds.tlsattacker.core.record.cipher.cryptohelper.EncryptionRequest;
import de.rub.nds.tlsattacker.core.record.cipher.cryptohelper.EncryptionResult;
import de.rub.nds.tlsattacker.core.record.cipher.cryptohelper.KeySet;
import de.rub.nds.tlsattacker.core.state.TlsContext;
import de.rub.nds.tlsattacker.transport.ConnectionEndType;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RecordBlockCipher extends RecordCipher {

    /**
     * indicates if explicit IV values should be used (as in TLS 1.1 and higher)
     */
    private boolean useExplicitIv;
    /**
     * mac for verification of incoming messages
     */
    private Mac readMac;
    /**
     * mac object for macing outgoing messages
     */
    private Mac writeMac;

    public RecordBlockCipher(TlsContext context, KeySet keySet) {
        super(context, keySet);
        if (version.usesExplicitIv()) {
            useExplicitIv = true;
        }
        CipherAlgorithm cipherAlg = AlgorithmResolver.getCipher(cipherSuite);
        ConnectionEndType localConEndType = context.getConnection().getLocalConnectionEndType();

        try {
            encryptCipher = CipherWrapper.getEncryptionCipher(cipherAlg);
            decryptCipher = CipherWrapper.getDecryptionCipher(cipherAlg);
            MacAlgorithm macAlg = AlgorithmResolver.getMacAlgorithm(context.getChooser().getSelectedProtocolVersion(),
                    cipherSuite);
            readMac = Mac.getInstance(macAlg.getJavaName());
            writeMac = Mac.getInstance(macAlg.getJavaName());
            readMac.init(new SecretKeySpec(getKeySet().getReadMacSecret(localConEndType), readMac.getAlgorithm()));
            writeMac.init(new SecretKeySpec(getKeySet().getWriteMacSecret(localConEndType), writeMac.getAlgorithm()));
        } catch (NoSuchAlgorithmException | InvalidKeyException E) {
            throw new UnsupportedOperationException("Unsupported Ciphersuite:" + cipherSuite.name(), E);
        }
    }

    @Override
    public byte[] calculateMac(byte[] data, ConnectionEndType connectionEndType) {
        LOGGER.debug("The MAC was calculated over the following data: {}", ArrayConverter.bytesToHexString(data));
        byte[] result;
        if (connectionEndType == context.getChooser().getConnectionEndType()) {
            writeMac.update(data);
            result = writeMac.doFinal();

        } else {
            readMac.update(data);
            result = readMac.doFinal();
        }
        LOGGER.debug("MAC: {}", ArrayConverter.bytesToHexString(result));
        return result;
    }

    /**
     * Takes correctly padded data and encrypts it
     *
     * @param request
     *            The RequestedEncryption operation
     * @return The EncryptionResult
     */
    @Override
    public EncryptionResult encrypt(EncryptionRequest request) {
        try {
            byte[] ciphertext = encryptCipher.encrypt(getKeySet().getWriteKey(context.getTalkingConnectionEndType()),
                    request.getInitialisationVector(), request.getPlainText());
            if (!useExplicitIv) {
                encryptCipher.setIv(extractNextEncryptIv(ciphertext));
            }
            LOGGER.debug("EncryptIv: " + ArrayConverter.bytesToHexString(encryptCipher.getIv()));
            return new EncryptionResult(encryptCipher.getIv(), ciphertext, useExplicitIv);

        } catch (CryptoException ex) {
            LOGGER.warn("Could not decrypt Data with the provided parameters. Returning unencrypted data.", ex);
            return new EncryptionResult(request.getPlainText());
        }
    }

    private byte[] extractNextEncryptIv(byte[] ciphertext) {
        return Arrays.copyOfRange(ciphertext, ciphertext.length - encryptCipher.getBlocksize(), ciphertext.length);
    }

    /**
     * Takes a ciphertext and decrypts it
     *
     * @param decryptionRequest
     * @return The raw decrypted Bytes
     */
    @Override
    public DecryptionResult decrypt(DecryptionRequest decryptionRequest) {
        try {
            byte[] plaintext;
            byte[] usedIv;
            if (decryptionRequest.getCipherText().length % decryptCipher.getBlocksize() != 0) {
                LOGGER.warn("Ciphertext is not a multiple of the Blocksize. Not Decrypting");
                return new DecryptionResult(new byte[0], decryptionRequest.getCipherText(), useExplicitIv);
            }
            ConnectionEndType localConEndType = context.getConnection().getLocalConnectionEndType();
            if (useExplicitIv) {
                byte[] decryptIv = Arrays.copyOf(decryptionRequest.getCipherText(), decryptCipher.getBlocksize());
                LOGGER.debug("decryptionIV: " + ArrayConverter.bytesToHexString(decryptIv));
                plaintext = decryptCipher.decrypt(getKeySet().getReadKey(localConEndType), decryptIv, Arrays
                        .copyOfRange(decryptionRequest.getCipherText(), decryptCipher.getBlocksize(),
                                decryptionRequest.getCipherText().length));
                usedIv = decryptCipher.getIv();
            } else {
                byte[] decryptIv = getDecryptionIV();
                LOGGER.debug("decryptionIV: " + ArrayConverter.bytesToHexString(decryptIv));
                plaintext = decryptCipher.decrypt(getKeySet().getReadKey(localConEndType), decryptIv,
                        decryptionRequest.getCipherText());
                usedIv = decryptCipher.getIv();
                // Set next IV
            }

            return new DecryptionResult(usedIv, plaintext, useExplicitIv);
        } catch (CryptoException | UnsupportedOperationException ex) {
            LOGGER.warn("Could not decrypt Data with the provided parameters. Returning undecrypted data.", ex);
            return new DecryptionResult(null, decryptionRequest.getCipherText(), useExplicitIv);
        }
    }

    @Override
    public int getMacLength() {
        return readMac.getMacLength();
    }

    @Override
    public byte[] calculatePadding(int paddingLength) {
        paddingLength = Math.abs(paddingLength); // TODO why?!
        byte[] padding = new byte[paddingLength];
        for (int i = 0; i < paddingLength; i++) {
            padding[i] = (byte) (paddingLength - 1);
        }
        return padding;
    }

    @Override
    public int calculatePaddingLength(int dataLength) {
        return encryptCipher.getBlocksize() - (dataLength % encryptCipher.getBlocksize());
    }

    @Override
    public boolean isUsingPadding() {
        return true;
    }

    @Override
    public boolean isUsingMac() {
        return true;
    }

    @Override
    public boolean isUsingTags() {
        return false;
    }

    @Override
    public byte[] getEncryptionIV() {
        if (useExplicitIv) {
            CipherAlgorithm cipherAlgorithm = AlgorithmResolver.getCipher(cipherSuite);
            byte[] iv = new byte[cipherAlgorithm.getNonceBytesFromHandshake()];
            context.getRandom().nextBytes(iv);
            return iv;
        } else {
            byte[] tempIv = encryptCipher.getIv();
            if (tempIv == null) {
                ConnectionEndType localConEndType = context.getConnection().getLocalConnectionEndType();
                return getKeySet().getWriteIv(localConEndType);
            } else {
                return tempIv;
            }
        }
    }

    @Override
    public byte[] getDecryptionIV() {
        if (useExplicitIv) {
            return new byte[0];
        } else {
            byte[] tempIv = decryptCipher.getIv();
            if (tempIv == null) {
                ConnectionEndType localConEndType = context.getConnection().getLocalConnectionEndType();
                return getKeySet().getReadIv(localConEndType);
            } else {
                return tempIv;
            }
        }
    }
}
