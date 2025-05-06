package com.dzboot.ovpn.helpers

import android.util.Base64
import com.dzboot.ovpn.constants.Constants

import org.lsposed.lsparanoid.Obfuscate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher


@Obfuscate
object CipherHelper {

	fun getToken(): String {
		val pk = Constants.PUBLIC_KEY.replace("\\r".toRegex(), "")
			.replace("\\n".toRegex(), "")
			.replace(System.lineSeparator().toRegex(), "")
			.replace("-----BEGIN PUBLIC KEY-----", "")
			.replace("-----END PUBLIC KEY-----", "")

		var encoded = ""
		val encrypted: ByteArray?
		try {
			val publicBytes: ByteArray = Base64.decode(pk, Base64.DEFAULT)
			val keySpec = X509EncodedKeySpec(publicBytes)
			val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
			val pubKey: PublicKey = keyFactory.generatePublic(keySpec)
			val cipher: Cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING")
			cipher.init(Cipher.ENCRYPT_MODE, pubKey)
			encrypted = cipher.doFinal(System.currentTimeMillis().toString().toByteArray())
			encoded = Base64.encodeToString(encrypted, Base64.DEFAULT)
		} catch (e: Exception) {
			e.printStackTrace()
		}
		return URLEncoder.encode(encoded, "UTF-8")
	}
}