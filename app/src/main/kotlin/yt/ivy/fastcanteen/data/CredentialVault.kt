package yt.ivy.fastcanteen.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredLogin(val address: String, val username: String, val password: String)

interface CredentialStore {
    fun load(): StoredLogin?
    fun save(login: StoredLogin)
    fun clear()
}

/** Keeps the login encrypted with a non-exportable Android Keystore key. */
class CredentialVault(context: Context) : CredentialStore {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)

    override fun load(): StoredLogin? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return try {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            val plain = cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)
            DataInputStream(ByteArrayInputStream(plain)).use {
                StoredLogin(it.readUTF(), it.readUTF(), it.readUTF())
            }
        } catch (_: GeneralSecurityException) {
            // The key is gone (e.g. after a lock screen reset); the stored login cannot be recovered.
            clear()
            null
        } catch (_: IllegalArgumentException) {
            clear()
            null
        }
    }

    override fun save(login: StoredLogin) {
        val plain = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).use {
                it.writeUTF(login.address)
                it.writeUTF(login.username)
                it.writeUTF(login.password)
            }
        }.toByteArray()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(plain)
        prefs.edit().putString(KEY_BLOB, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_BLOB).apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "fastcanteen_vault"
        const val KEY_BLOB = "blob"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
