package client;

import org.bouncycastle.util.encoders.Base64;
import util.CommunicationChannel;
import util.HandshakeFailedException;
import util.HandshakePerformer;
import util.LineReader;
import util.crypto.CryptoChannel;
import util.crypto.cryptors.AESMessageCryptor;
import util.crypto.cryptors.RSAMessageCryptor;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.SecureRandom;
import java.util.logging.Logger;

/**
 * Performs the Client-Side Handshake needed for Lab2
 */
public class ClientHandshakePerformer implements HandshakePerformer {
    private static final Logger logger = Logger.getLogger("ClientHandshakePerformer");

    private String username;
    private Key clientPrivateKey, serverPublicKey;

    public ClientHandshakePerformer(String username, Key clientPrivateKey, Key serverPublicKey) {
        this.username = username;
        this.clientPrivateKey = clientPrivateKey;
        this.serverPublicKey = serverPublicKey;
    }

    @Override
    public CommunicationChannel execute(CommunicationChannel start) throws HandshakeFailedException {
        SecureRandom secureRandom = new SecureRandom();

        // Message 1
        byte[] clientChallenge = new byte[32];
        secureRandom.nextBytes(clientChallenge);
        String encodedClientChallenge = new String(Base64.encode(clientChallenge));

        String msg1 = "!authenticate " + this.username + " " + encodedClientChallenge + "\n";
        logger.info("Message 1: " + msg1);
        try {
            CommunicationChannel rsaChannel = new CryptoChannel(start, new RSAMessageCryptor(serverPublicKey, clientPrivateKey));

            OutputStreamWriter msg1Writer = new OutputStreamWriter(rsaChannel.getOutputStream());
            msg1Writer.write(msg1);
            msg1Writer.flush();

            // Message 2
            LineReader reader = new LineReader(rsaChannel.getInputStream());
            logger.info("Waiting for server response...");
            String msg2 = reader.readLine();

            logger.info("Got server message: " + msg2);

            // parse message 2
            // Format: !ok <client-challenge> <chatserver-challenge> <secret-key> <iv-parameter>
            String[] msg2Split = msg2.split(" ");
            if (msg2Split.length != 5) {
                throw new HandshakeFailedException("Message 2 is incorrect: Incorrect length (msg: \"" + msg2 + "\")");
            }
            if (!msg2Split[0].equals("!ok")) {
                throw new HandshakeFailedException("Server did not return !ok. (result: \"" + msg2 + "\")");
            }

            // read sent challenge and check
            byte[] msg2_clientChallenge = Base64.decode(msg2Split[1]);
            if (!compareArrays(clientChallenge, msg2_clientChallenge)) {
                throw new HandshakeFailedException("Server sent invalid challenge");
            }

            // read rest of the arguments
            String msg2_serverChallengeB64 = msg2Split[2];
            byte[] msg2_secretKey = Base64.decode(msg2Split[3]);
            byte[] msg2_iv = Base64.decode(msg2Split[4]);


            // change crypto channel for message 3
            CommunicationChannel aesChannel = new CryptoChannel(start, new AESMessageCryptor(msg2_iv, msg2_secretKey));


            // Message 3
            OutputStreamWriter msg3Writer = new OutputStreamWriter(aesChannel.getOutputStream());

            logger.info("Sending message 3");
            String msg3 = msg2_serverChallengeB64 + "\n";
            msg3Writer.write(msg3);
            msg3Writer.flush();

            logger.info("Handshake finished");

            return aesChannel;
        } catch (InvalidKeyException | IOException e) {
            throw new HandshakeFailedException(e);
        }
    }

    private static boolean compareArrays(byte[] a1, byte[] a2) {
        if (a1 == a2) {
            return true;
        }

        if (a1.length != a2.length) {
            return false;
        }

        for (int i = 0; i < a1.length; ++i) {
            if (a1[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }
}
