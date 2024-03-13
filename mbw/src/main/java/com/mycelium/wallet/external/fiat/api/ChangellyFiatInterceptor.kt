package com.mycelium.wallet.external.fiat.api

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Response
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * This Interceptor is necessary to comply with changelly's authentication scheme and follows
 * roughly their example implementation in JS:
 * https://github.com/changelly/api-changelly#authentication
 *
 * It wraps the parameters passed in, in a params object and signs the request with the api key secret.
 */
class ChangellyFiatInterceptor : Interceptor {
    private companion object {
        const val API_HEADER_KEY = "X-Api-Key"
        const val SIGN_HEADER_KEY = "X-Api-Signature"
        const val PRIVATE_KEY_BASE64 =
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCWwnwzJHpcFg9S0oCgofDE8M8AbO2S5Zsn/ycBh1X2c6ZwmizRGeEEsKNdVmTuJgVwrWjo68G6XB0p7hwhXYTTyN36GnQAopzD3aOLwbxIdywCr8ayD3crRZtXUcR4cei4/qR/2xBymJL7wGRVS/LHkdrlrg6M5s8yvu3slB8aCk2DZoCIzpcuHITn67Fhy4wVLF5hJ2TGZLsS79ZG2gE0wXNxZFq7/2NX5/QJWoArUI6FERpEDpXLaO05KIhj3SDtMYTcop68/w4Bb2B2oCRqAxMgC0iYV4kHlaBFHKw4WL6rJpZBhSVu6NPZfRwzRp2xqurpJ4J2Vu5SOt7LTHcLAgMBAAECggEAGOm+uHzYs4r5sUgT9XeNYB4jNwDJKbNDtpJVkc0ZiYaHBKiVq2BJbQr7lBsIxsoFsB8X7RW4h+Fc+Gbewyy/HHhfJktkzA4TNdLUie3T/W2kGjWN8jLYEJCKIR3kB7tbJ+b8rBw5VZadA3lBm07Xqd8Se04OHX6u0sX2b1fy48x7IHTT93dJgxu9/T2qeDTCg78EDzk3KSbIUM/D48AWf4GB0Gh35uPBVmNdB65gPxJj6nte6rQcbg4Rq0mpD47wlRxE+7nDI93ZUx1Sw01X8cYDPNXup0V2+og5z/nGwBhAuCCl1HemC2gNnyE0zWsKQJXoFy59GzjwlR5q2m0wEQKBgQDDPPYmNJGyMK9i7GE30VAhTwBYZJDa5noI73IDfbpneeF5VhLyXI5sxKxqB4ce1om2Tv07q8ubHU3LvQVo34cVImjEBFNRDV9SzgJMj8aDXTzykYFY9VyL7cv7BVZyG/M9i4WIxFmJ3oOe0KKUYlzEsyZ3oktlFfx/yKW2jsn7jwKBgQDFrdUux+SPRODKl9gDtUf2swlHsdyXt8fdsIOfK+5VPI2XQSHXNACtV1E0jfdPzgZcO5QYsgC3+DkcQKHoyimyoNSF2DKWHywQ16Xb9ZkFP7D4ioo7WC/WYbah40PGbeyNjFTwb2DH+ocgiTxkxQ0HYYJ++b0kYUw0z9vJzlr+xQKBgQCX4xA4DsOeFcQMOIs1anBlSVmiarJKqe0ckHNpheGDaM1hoPXieOEQez0Ky8px0kOWggL97hvbE8QXEXlo6iTj6z4H6LmMn9Odzsj5GQ0920Z0C4DOXSvfbGM5aSCka1wjcCy1htOFr4dEAVnKCG/VUu5FOgxfmugx3T4WNSWrvwKBgQCdm4dJER7uC6CeZopYCoUh8IUJoImcfW7hFgcbNU+Erg7F9awhhNi1W9Tm7fmsqzru9qGRPvrLsyD1oaJ1lBnCzfAj9sf+YUQk+YrH4Pzr7mgPAYZM4nbhmm6ejDBRWPstsCFYwbSMPPbXvhNk1Kcap2gADOX8x4aW5NL8B4cNcQKBgDGG/vJ2zLrUEWcxrGD8aecYln4B9HNkF4yvXSgQp+LH/pTgDuhbv5EN0AcgfgbJvUV3k7reM1vZ33yU1PUVdDbJ/ovFEwklt8qAJpnQIKCXzI8Ekmi2dZPE3QHBcJx9cxOG8M3l0EBftUd+aPYLKzeJ4sn2t9g02rwlZ3EoKFf/"
        const val PUBLIC_KEY_BASE64 =
            "5539bd3ed5025b69632b833d8d64f5556ee134dea64ca306ff6c915587a0fbd3"

        val privateKey = getChangellyApiKey()
        fun getChangellyApiKey(): PrivateKey {
            val privateKeyBytes = Base64.decode(PRIVATE_KEY_BASE64, Base64.NO_WRAP)
            val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePrivate(keySpec)
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body()
        val newRequest = if (body != null && body.contentLength() != -1L) {
            // post request
            val buffer = okio.Buffer()
            body.writeTo(buffer)
            val bodyString = buffer.readUtf8()
            val payload = "${request.url()}$bodyString"
            request.newBuilder()
                .addHeader(API_HEADER_KEY, PUBLIC_KEY_BASE64)
                .addHeader(SIGN_HEADER_KEY, getSignature(payload.toByteArray()))
                .post(body)
                .build()
        } else {
            // get request
            val payload = "${request.url()}{}"
            request.newBuilder()
                .addHeader(API_HEADER_KEY, PUBLIC_KEY_BASE64)
                .addHeader(SIGN_HEADER_KEY, getSignature(payload.toByteArray()))
                .get()
                .build()
        }
        return chain.proceed(newRequest)
    }

    private fun getSignature(data: ByteArray): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(data)
        val signedData = signature.sign()
        return Base64.encodeToString(signedData, Base64.NO_WRAP)
    }
}
