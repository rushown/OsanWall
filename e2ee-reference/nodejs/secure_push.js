/**
 * Privacy-preserving push trigger (Node.js reference).
 * FCM/APNs only receive: opaque device token + ciphertext blob + silent flag.
 * No sender/recipient ids, no chat id, no content hints.
 *
 * Deploy: this runs behind your API after authenticating a blinded session token.
 */

'use strict';

const crypto = require('crypto');

/**
 * @param {object} opts
 * @param {string} opts.devicePushToken - FCM/APNs registration id (already server-side mapping exists only as hash)
 * @param {Buffer} opts.wrappedPayload - ciphertext the client will decrypt (e.g. "new_message" with no metadata)
 * @param {Buffer} opts.hmacKey - server HMAC key for webhook verification (rotated weekly)
 */
function buildSilentPushBody(opts) {
  const { devicePushToken, wrappedPayload, hmacKey } = opts;
  const nonce = crypto.randomBytes(12);
  const mac = crypto.createHmac('sha256', hmacKey);
  mac.update(devicePushToken);
  mac.update(wrappedPayload);
  mac.update(nonce);
  const sig = mac.digest();
  return {
    to: devicePushToken,
    priority: 'high',
    content_available: true,
    data: {
      e: wrappedPayload.toString('base64'),
      n: nonce.toString('base64'),
      s: sig.toString('base64'),
    },
    // Omit notification.title/body to avoid leaking to lock screen
  };
}

/**
 * Rate limit key: HMAC(server_pepper, blinded_session_token) — never raw IP.
 */
function rateLimitKey(serverPepper, blindedSessionToken) {
  return crypto.createHmac('sha256', serverPepper).update(blindedSessionToken).digest();
}

module.exports = { buildSilentPushBody, rateLimitKey };
