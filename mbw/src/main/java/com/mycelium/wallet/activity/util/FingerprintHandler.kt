package com.mycelium.wallet.activity.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.support.annotation.RequiresApi
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat
import android.support.v4.os.CancellationSignal
import android.util.Log
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey


class FingerprintHandler {

    private val cancelSignal = CancellationSignal()
    private var keyStore: KeyStore? = null
    fun startAuth(context: Context, success: () -> Unit) {
        generateKey()
        val cipher = Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES + "/"
                        + KeyProperties.BLOCK_MODE_CBC + "/"
                        + KeyProperties.ENCRYPTION_PADDING_PKCS7)

        keyStore?.load(null)
        val key = keyStore?.getKey("key", null) as SecretKey
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val cryptoObject = FingerprintManagerCompat.CryptoObject(cipher)

        val fingerprintManagerCompat = FingerprintManagerCompat.from(context)
        fingerprintManagerCompat.authenticate(cryptoObject, 0, cancelSignal, Callback(success), null)
    }

    fun cancelAuth() {
        cancelSignal.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun generateKey() {
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore?.load(null);

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            keyGenerator.init(KeyGenParameterSpec.Builder("key", KeyProperties.PURPOSE_ENCRYPT.or(KeyProperties.PURPOSE_DECRYPT))
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build())

            keyGenerator.generateKey();

        } catch (exc: KeyStoreException) {
            exc.printStackTrace();
        }
    }

    class Callback(val success: () -> Unit) : FingerprintManagerCompat.AuthenticationCallback() {
        override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
            super.onAuthenticationError(errMsgId, errString)
            Log.e("!!!", "onAuthenticationError")
        }

        override fun onAuthenticationSucceeded(result: FingerprintManagerCompat.AuthenticationResult?) {
            super.onAuthenticationSucceeded(result)
            success.invoke()
        }

        override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
            super.onAuthenticationHelp(helpMsgId, helpString)
            Log.e("!!!", "onAuthenticationHelp")
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            Log.e("!!!", "onAuthenticationFailed")
        }
    }

    companion object {
        /*
        * Condition II: Check if the device has fingerprint sensors.
        * Note: If you marked android.hardware.fingerprint as something that
        * your app requires (android:required="true"), then you don't need
        * to perform this check.
        *
        * */
        fun isHardwareSupported(context: Context): Boolean {
            val fingerprintManager = FingerprintManagerCompat.from(context)
            return fingerprintManager.isHardwareDetected
        }

        /*
        * Condition III: Fingerprint authentication can be matched with a
        * registered fingerprint of the user. So we need to perform this check
        * in order to enable fingerprint authentication
        * */
        fun isFingerprintAvailable(context: Context): Boolean {
            val fingerprintManager = FingerprintManagerCompat.from(context)
            return fingerprintManager.hasEnrolledFingerprints()
        }
    }
}