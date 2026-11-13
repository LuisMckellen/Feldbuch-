package com.example.data.twa

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

object AppSigningFingerprint {

  fun formatSha256ColonHex(certBytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
    return digest.joinToString(":") { byte -> "%02X".format(byte) }
  }

  fun readSha256(context: Context): String {
    val signature = firstSigningSignature(context) ?: return ""
    return runCatching { formatSha256ColonHex(signature.toByteArray()) }.getOrDefault("")
  }

  fun buildAssetLinksSnippet(packageName: String, sha256Fingerprint: String): String {
    val pkg = packageName.trim()
    val fp = sha256Fingerprint.trim()
    val relationArray = JSONArray().put("delegate_permission/common.handle_all_urls")
    val fingerprintsArray = JSONArray().apply { if (fp.isNotEmpty()) put(fp) }
    val target =
      JSONObject()
        .put("namespace", "android_app")
        .put("package_name", pkg)
        .put("sha256_cert_fingerprints", fingerprintsArray)
    val entry = JSONObject().put("relation", relationArray).put("target", target)
    return JSONArray().put(entry).toString(2)
  }

  @Suppress("DEPRECATION")
  private fun firstSigningSignature(context: Context): Signature? =
    try {
      val pm = context.packageManager
      val pkgName = context.packageName
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signingInfo = info.signingInfo ?: return null
        val certs =
          if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
          else signingInfo.signingCertificateHistory
        certs?.firstOrNull()
      } else {
        val info = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
        info.signatures?.firstOrNull()
      }
    } catch (_: Exception) {
      null
    }
}
