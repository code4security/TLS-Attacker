/**
 * TLS-Attacker - A Modular Penetration Testing Framework for TLS
 *
 * Copyright 2014-2017 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsattacker.core.protocol.handler;

import de.rub.nds.modifiablevariable.util.ArrayConverter;
import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.CompressionMethod;
import de.rub.nds.tlsattacker.core.constants.DigestAlgorithm;
import de.rub.nds.tlsattacker.core.constants.ECPointFormat;
import de.rub.nds.tlsattacker.core.constants.EllipticCurveType;
import de.rub.nds.tlsattacker.core.constants.HKDFAlgorithm;
import de.rub.nds.tlsattacker.core.constants.HandshakeMessageType;
import de.rub.nds.tlsattacker.core.constants.NamedGroup;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.constants.Tls13KeySetType;
import de.rub.nds.tlsattacker.core.crypto.ECCUtilsBCWrapper;
import de.rub.nds.tlsattacker.core.crypto.HKDFunction;
import de.rub.nds.tlsattacker.core.crypto.ec.Curve25519;
import de.rub.nds.tlsattacker.core.exceptions.AdjustmentException;
import de.rub.nds.tlsattacker.core.exceptions.CryptoException;
import de.rub.nds.tlsattacker.core.exceptions.PreparationException;
import static de.rub.nds.tlsattacker.core.protocol.handler.ProtocolMessageHandler.LOGGER;
import de.rub.nds.tlsattacker.core.protocol.message.ServerHelloMessage;
import de.rub.nds.tlsattacker.core.protocol.message.extension.KS.KeyShareStoreEntry;
import de.rub.nds.tlsattacker.core.protocol.parser.ServerHelloParser;
import de.rub.nds.tlsattacker.core.protocol.preparator.ServerHelloPreparator;
import de.rub.nds.tlsattacker.core.protocol.serializer.ServerHelloSerializer;
import de.rub.nds.tlsattacker.core.record.cipher.RecordCipher;
import de.rub.nds.tlsattacker.core.record.cipher.RecordCipherFactory;
import de.rub.nds.tlsattacker.core.record.cipher.cryptohelper.KeySet;
import de.rub.nds.tlsattacker.core.record.cipher.cryptohelper.KeySetGenerator;
import de.rub.nds.tlsattacker.core.state.Session;
import de.rub.nds.tlsattacker.core.state.TlsContext;
import de.rub.nds.tlsattacker.core.workflow.chooser.Chooser;
import de.rub.nds.tlsattacker.transport.ConnectionEndType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Mac;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.tls.TlsECCUtils;
import org.bouncycastle.math.ec.ECPoint;

public class ServerHelloHandler extends HandshakeMessageHandler<ServerHelloMessage> {

    public ServerHelloHandler(TlsContext tlsContext) {
        super(tlsContext);
    }

    @Override
    public ServerHelloPreparator getPreparator(ServerHelloMessage message) {
        return new ServerHelloPreparator(tlsContext.getChooser(), message);
    }

    @Override
    public ServerHelloSerializer getSerializer(ServerHelloMessage message) {
        return new ServerHelloSerializer(message, tlsContext.getChooser().getSelectedProtocolVersion());
    }

    @Override
    public ServerHelloParser getParser(byte[] message, int pointer) {
        return new ServerHelloParser(pointer, message, tlsContext.getChooser().getLastRecordVersion());
    }

    @Override
    public void adjustTLSContext(ServerHelloMessage message) {
        adjustSelectedProtocolVersion(message);
        if (!tlsContext.getChooser().getSelectedProtocolVersion().isTLS13()) {
            adjustSelectedCompression(message);
            adjustSelectedSessionID(message);
        }
        adjustSelectedCiphersuite(message);
        adjustServerRandom(message);
        adjustExtensions(message, HandshakeMessageType.SERVER_HELLO);
        if (tlsContext.getChooser().getSelectedProtocolVersion().isTLS13()) {
            adjustHandshakeTrafficSecrets();
            if (tlsContext.getTalkingConnectionEndType() != tlsContext.getChooser().getConnectionEndType()) {
                setServerRecordCipher();
            }
        }
        adjustPRF(message);
        if (tlsContext.hasSession(tlsContext.getChooser().getServerSessionId())) {
            LOGGER.info("Resuming Session");
            LOGGER.debug("Loading Mastersecret");
            Session session = tlsContext.getSession(tlsContext.getChooser().getServerSessionId());
            tlsContext.setMasterSecret(session.getMasterSecret());
            setRecordCipher();
        }
    }

    private void adjustSelectedCiphersuite(ServerHelloMessage message) {
        CipherSuite suite = CipherSuite.getCipherSuite(message.getSelectedCipherSuite().getValue());
        if (suite != null) {
            tlsContext.setSelectedCipherSuite(suite);
            LOGGER.debug("Set SelectedCipherSuite in Context to " + suite.name());
        } else {
            LOGGER.warn("Unknown CipherSuite, did not adjust Context");
        }
    }

    private void adjustServerRandom(ServerHelloMessage message) {
        tlsContext.setServerRandom(message.getRandom().getValue());
        LOGGER.debug("Set ServerRandom in Context to " + ArrayConverter.bytesToHexString(tlsContext.getServerRandom()));
    }

    private void adjustSelectedCompression(ServerHelloMessage message) {
        if (message.getSelectedCompressionMethod() != null) {
            CompressionMethod method = CompressionMethod.getCompressionMethod(message.getSelectedCompressionMethod()
                    .getValue());
            if (method != null) {
                tlsContext.setSelectedCompressionMethod(method);
                LOGGER.debug("Set SelectedCompressionMethod in Context to " + method.name());
            } else {
                LOGGER.warn("Unknown CompressionAlgorithm, did not adjust Context");
            }
        } else {
            LOGGER.warn("Not adjusting CompressionMethod - Method is null!");
        }
    }

    private void adjustSelectedSessionID(ServerHelloMessage message) {
        byte[] sessionID = message.getSessionId().getValue();
        tlsContext.setServerSessionId(sessionID);
        LOGGER.debug("Set SessionID in Context to " + ArrayConverter.bytesToHexString(sessionID, false));

    }

    private void adjustSelectedProtocolVersion(ServerHelloMessage message) {
        ProtocolVersion version = ProtocolVersion.getProtocolVersion(message.getProtocolVersion().getValue());
        if (version != null) {
            tlsContext.setSelectedProtocolVersion(version);
            LOGGER.debug("Set SelectedProtocolVersion in Context to " + version.name());
        } else {
            LOGGER.warn("Did not Adjust ProtocolVersion since version is undefined "
                    + ArrayConverter.bytesToHexString(message.getProtocolVersion().getValue()));
        }
    }

    private void adjustPRF(ServerHelloMessage message) {
        Chooser chooser = tlsContext.getChooser();
        if (!chooser.getSelectedProtocolVersion().isSSL()) {
            tlsContext.setPrfAlgorithm(AlgorithmResolver.getPRFAlgorithm(chooser.getSelectedProtocolVersion(),
                    chooser.getSelectedCipherSuite()));
        }
    }

    private void setRecordCipher() {
        KeySet keySet = getKeySet(tlsContext, Tls13KeySetType.NONE);
        LOGGER.debug("Setting new Cipher in RecordLayer");
        RecordCipher recordCipher = RecordCipherFactory.getRecordCipher(tlsContext, keySet);
        tlsContext.getRecordLayer().setRecordCipher(recordCipher);
    }

    private void setServerRecordCipher() {
        tlsContext.setTls13SoftDecryption(true);
        tlsContext.setActiveServerKeySetType(Tls13KeySetType.HANDSHAKE_TRAFFIC_SECRETS);
        LOGGER.debug("Setting cipher for server to use handshake secrets");
        KeySet serverKeySet = getKeySet(tlsContext, tlsContext.getActiveServerKeySetType());
        RecordCipher recordCipherServer = RecordCipherFactory.getRecordCipher(tlsContext, serverKeySet, tlsContext
                .getChooser().getSelectedCipherSuite());
        tlsContext.getRecordLayer().setRecordCipher(recordCipherServer);

        if (tlsContext.getChooser().getConnectionEndType() == ConnectionEndType.CLIENT) {
            tlsContext.setReadSequenceNumber(0);
            tlsContext.getRecordLayer().updateDecryptionCipher();
        } else {
            tlsContext.setWriteSequenceNumber(0);
            tlsContext.getRecordLayer().updateEncryptionCipher();
        }
    }

    private KeySet getKeySet(TlsContext context, Tls13KeySetType keySetType) {
        try {
            LOGGER.debug("Generating new KeySet");
            return KeySetGenerator.generateKeySet(context, tlsContext.getChooser().getSelectedProtocolVersion(),
                    keySetType);
        } catch (NoSuchAlgorithmException | CryptoException ex) {
            throw new UnsupportedOperationException("The specified Algorithm is not supported", ex);
        }
    }

    @Override
    public void adjustTlsContextAfterSerialize(ServerHelloMessage message) {
        if (tlsContext.getChooser().getSelectedProtocolVersion().isTLS13()) {
            setServerRecordCipher();
        }
    }

    private void adjustHandshakeTrafficSecrets() {
        HKDFAlgorithm hkdfAlgortihm = AlgorithmResolver.getHKDFAlgorithm(tlsContext.getChooser()
                .getSelectedCipherSuite());
        DigestAlgorithm digestAlgo = AlgorithmResolver.getDigestAlgorithm(tlsContext.getChooser()
                .getSelectedProtocolVersion(), tlsContext.getChooser().getSelectedCipherSuite());

        try {
            int macLength = Mac.getInstance(hkdfAlgortihm.getMacAlgorithm().getJavaName()).getMacLength();
            byte[] psk = (tlsContext.getConfig().isUsePsk() || tlsContext.getPsk() != null) ? tlsContext.getChooser()
                    .getPsk() : new byte[macLength]; // use PSK if available
            byte[] earlySecret = HKDFunction.extract(hkdfAlgortihm, new byte[0], psk);
            byte[] saltHandshakeSecret = HKDFunction.deriveSecret(hkdfAlgortihm, digestAlgo.getJavaName(), earlySecret,
                    HKDFunction.DERIVED, ArrayConverter.hexStringToByteArray(""));
            byte[] sharedSecret = new byte[macLength];
            if (tlsContext.getChooser().getConnectionEndType() == ConnectionEndType.CLIENT) {
                sharedSecret = computeSharedSecret(tlsContext.getChooser().getServerKeyShare());
            } else {
                Integer pos = null;
                for (KeyShareStoreEntry entry : tlsContext.getChooser().getClientKeyShares()) {
                    if (Arrays.equals(entry.getGroup().getValue(), tlsContext.getChooser().getServerKeyShare()
                            .getGroup().getValue())) {
                        pos = tlsContext.getChooser().getClientKeyShares().indexOf(entry);
                    }
                }
                if (pos == null) {
                    LOGGER.warn("Client did not send the KeyShareType we expected. Choosing first in his List");
                    pos = 0;
                }
                sharedSecret = computeSharedSecret(tlsContext.getChooser().getClientKeyShares().get(pos));
            }
            byte[] handshakeSecret = HKDFunction.extract(hkdfAlgortihm, saltHandshakeSecret, sharedSecret);
            tlsContext.setHandshakeSecret(handshakeSecret);
            LOGGER.debug("Set handshakeSecret in Context to " + ArrayConverter.bytesToHexString(handshakeSecret));
            byte[] clientHandshakeTrafficSecret = HKDFunction.deriveSecret(hkdfAlgortihm, digestAlgo.getJavaName(),
                    handshakeSecret, HKDFunction.CLIENT_HANDSHAKE_TRAFFIC_SECRET, tlsContext.getDigest().getRawBytes());
            tlsContext.setClientHandshakeTrafficSecret(clientHandshakeTrafficSecret);
            LOGGER.debug("Set clientHandshakeTrafficSecret in Context to "
                    + ArrayConverter.bytesToHexString(clientHandshakeTrafficSecret));
            byte[] serverHandshakeTrafficSecret = HKDFunction.deriveSecret(hkdfAlgortihm, digestAlgo.getJavaName(),
                    handshakeSecret, HKDFunction.SERVER_HANDSHAKE_TRAFFIC_SECRET, tlsContext.getDigest().getRawBytes());
            tlsContext.setServerHandshakeTrafficSecret(serverHandshakeTrafficSecret);
            LOGGER.debug("Set serverHandshakeTrafficSecret in Context to "
                    + ArrayConverter.bytesToHexString(serverHandshakeTrafficSecret));
        } catch (CryptoException | NoSuchAlgorithmException ex) {
            throw new AdjustmentException(ex);
        }
    }

    /**
     * Computes the shared secret for ECDH_X25519
     *
     * @return
     */
    private byte[] computeSharedSecret(KeyShareStoreEntry keyShare) {
        switch (keyShare.getGroup()) {
            case ECDH_X25519:
                BigInteger privateKey = tlsContext.getConfig().getKeySharePrivate();
                byte[] keySharePublicKey = keyShare.getPublicKey();
                byte[] privateKeyBytes;
                if (privateKey.toByteArray().length != 32) {
                    LOGGER.warn("ECDH_525519 private Key is not 32 byte - using as much as possible and padding the rest with Zeros.");
                    privateKeyBytes = Arrays.copyOf(privateKey.toByteArray(), 32);
                } else {
                    privateKeyBytes = privateKey.toByteArray();
                }
                LOGGER.debug("Clamping private key");
                Curve25519.clamp(privateKeyBytes);
                byte[] sharedSecret = new byte[32];
                Curve25519.curve(sharedSecret, privateKeyBytes, keySharePublicKey);
                return sharedSecret;
            case ECDH_X448:
                throw new UnsupportedOperationException("x448 not supported yet");
            case SECP160K1:
            case SECP160R1:
            case SECP160R2:
            case SECP192K1:
            case SECP192R1:
            case SECP224K1:
            case SECP224R1:
            case SECP256K1:
            case SECP256R1:
            case SECP384R1:
            case SECP521R1:
            case SECT163K1:
            case SECT163R1:
            case SECT163R2:
            case SECT193R1:
            case SECT193R2:
            case SECT233K1:
            case SECT233R1:
            case SECT239K1:
            case SECT283K1:
            case SECT283R1:
            case SECT409K1:
            case SECT409R1:
            case SECT571K1:
            case SECT571R1:
                ECDomainParameters generateEcParameters = generateEcParameters(keyShare.getGroup());
                ECPoint deserializeEcPoint = deserializeEcPoint(generateEcParameters, keyShare.getPublicKey());
                deserializeEcPoint.normalize();
                ECPublicKeyParameters params = new ECPublicKeyParameters(deserializeEcPoint, generateEcParameters);
                ECPrivateKeyParameters privParams = new ECPrivateKeyParameters(tlsContext.getConfig()
                        .getDefaultKeySharePrivateKey(), generateEcParameters);
                return TlsECCUtils.calculateECDHBasicAgreement(params, privParams);
            default:
                throw new UnsupportedOperationException("KeyShare type " + keyShare.getGroup() + " is unsupported");
        }
    }

    protected ECDomainParameters generateEcParameters(NamedGroup group) {
        InputStream is = new ByteArrayInputStream(ArrayConverter.concatenate(
                new byte[] { EllipticCurveType.NAMED_CURVE.getValue() }, group.getValue()));
        try {
            return ECCUtilsBCWrapper.readECParameters(group, ECPointFormat.UNCOMPRESSED, is);
        } catch (IOException ex) {
            throw new PreparationException("Failed to generate EC domain parameters", ex);
        }
    }

    protected ECPoint deserializeEcPoint(ECDomainParameters parameters, byte[] bytes) {
        ECPoint decodePoint = parameters.getCurve().decodePoint(bytes);
        return decodePoint;
    }
}
