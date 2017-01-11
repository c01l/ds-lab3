package util.crypto.cryptors;

import util.crypto.BrokenMessageException;

/**
 * Allows to encrypt/decrypt messages
 */
public interface MessageCryptor {

    /**
     * Encrypts the message given to it.
     *
     * @param msg the message to be encrypted (plaintext)
     * @return the encrypted message
     */
    String encrypt(String msg) throws BrokenMessageException;

    /**
     * Decrypts the message given to it.
     *
     * @param msg the encrypted message (ciphertext)
     * @return the original message (plaintext)
     */
    String decrypt(String msg) throws BrokenMessageException;
}
