package chatserver;

import org.bouncycastle.util.encoders.Base64;
import util.CommunicationChannel;
import util.HandshakeFailedException;
import util.HandshakePerformer;
import util.crypto.CryptoChannel;
import util.crypto.cryptors.AESMessageCryptor;
import util.crypto.cryptors.RSAMessageCryptor;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.logging.Logger;


public class ServerHandshakePerformer implements HandshakePerformer {

    private static final Logger logger = Logger.getLogger("ServerHandshakePerformer");

    private Key serverPrivateKey;
    private List<UserData> userDataList;

    private UserData lastLoggedIn = null;

    public ServerHandshakePerformer(Key serverPrivateKey, List<UserData> userDataList) {
        this.serverPrivateKey = serverPrivateKey;
        this.userDataList = userDataList;
    }

    @Override
    public CommunicationChannel execute(CommunicationChannel start) throws HandshakeFailedException {

        try {
            // Message 1
            // prepare channel
            RSAMessageCryptor rsaCryptor = new RSAMessageCryptor(null, this.serverPrivateKey); // needed to set clients public key later
            CommunicationChannel rsaChannel = new CryptoChannel(start, rsaCryptor);

            // recieve
            BufferedReader reader = new BufferedReader(new InputStreamReader(rsaChannel.getInputStream()));
            String msg1 = reader.readLine();

            logger.info("Client sent (message 1): " + msg1);

            if(msg1 == null) {
                throw new HandshakeFailedException("Client sent empty message");
            }

            // split and check message integrity
            String[] msg1Split = msg1.split(" ");
            if (msg1Split.length != 3) {
                throw new HandshakeFailedException("Message 1 has incorrect length. (msg: \"" + msg1 + "\")");
            }
            if (!msg1Split[0].equals("!authenticate")) {
                throw new HandshakeFailedException("Client does not want to login. '!authenticate' expected (msg: \"" + msg1 + "\")");
            }

            String username = msg1Split[1];
            String clientChallenge = msg1Split[2];

            // find user
            logger.info("Looking for user '" + username + "' ...");
            UserData user = findUserByName(username);
            if (user == null) {
                logger.info("Cannot find user: " + username);
                throw new HandshakeFailedException("Cannot find user");
            }

            // use users public key to respond
            rsaCryptor.setEncryptionKey(user.getPublicKey());

            logger.info("Prepare for sending message 2");

            // Message 2
            SecureRandom secureRandom = new SecureRandom();
            byte[] serverChallenge = new byte[32];
            secureRandom.nextBytes(serverChallenge);
            String serverChallengeEncodedB64 = new String(Base64.encode(serverChallenge));

            KeyGenerator aesGenerator = KeyGenerator.getInstance("AES");
            aesGenerator.init(256);
            SecretKey key = aesGenerator.generateKey();
            byte[] keyEncoded = key.getEncoded();
            String keyEncodedB64 = new String(Base64.encode(keyEncoded));

            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            String ivEncodedB64 = new String(Base64.encode(iv));

            // compile
            String msg2 = "!ok " + clientChallenge + " " + serverChallengeEncodedB64 + " " + keyEncodedB64 + " " + ivEncodedB64 + "\n";

            logger.info("Sending message: " + msg2);

            // send
            OutputStreamWriter rsaWriter = new OutputStreamWriter(rsaChannel.getOutputStream());
            rsaWriter.write(msg2);
            rsaWriter.flush();

            logger.info("Sent message!");

            // Message 3
            // initialize AES channel
            CommunicationChannel aesChannel = new CryptoChannel(start, new AESMessageCryptor(iv, keyEncoded));

            logger.info("Waiting for message 3...");

            // read server challenge from the new channel
            BufferedReader aesReader = new BufferedReader(new InputStreamReader(aesChannel.getInputStream()));
            String serverChallengeResponse = aesReader.readLine();
            if(serverChallengeResponse == null || !serverChallengeEncodedB64.equals(serverChallengeResponse)) {
                throw new HandshakeFailedException("Server Challenge was not returned correctly. (Got: " + serverChallengeResponse + ")");
            }

            logger.info("Finished handshake!");

            this.lastLoggedIn = user;

            return aesChannel;
        } catch (InvalidKeyException | IOException e) {
            throw new HandshakeFailedException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    public UserData getLastLoggedIn() {
        return lastLoggedIn;
    }

    private UserData findUserByName(String name) {
        synchronized (this.userDataList) {
            for (UserData u : this.userDataList) {
                synchronized (u) {
                    if (u.getName().equals(name)) {
                        return u;
                    }
                }
            }
        }
        return null;
    }
}
