package e2ee

import (
	"context"
	"database/sql"
	"errors"
	"time"
)

// InsertCiphertext stores one padded, encrypted chunk; relies on UNIQUE (recipient_blind_id, idempotency_hash).
func InsertCiphertext(ctx context.Context, db *sql.DB,
	recipientBlind, deliveryTag, ciphertext, encryptedHeader, idemHash []byte,
	paddedLen int, expiresAt time.Time, authTag, innerHMAC []byte,
) error {
	const q = `INSERT INTO e2ee_messages (
		delivery_tag, recipient_blind_id, ciphertext, auth_tag, inner_hmac,
		padded_len, expires_at, encrypted_header, idempotency_hash
	) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)
	ON CONFLICT (recipient_blind_id, idempotency_hash) DO NOTHING`
	_, err := db.ExecContext(ctx, q,
		deliveryTag, recipientBlind, ciphertext, nullIfEmpty(authTag), nullIfEmpty(innerHMAC),
		paddedLen, expiresAt, encryptedHeader, idemHash,
	)
	return err
}

func nullIfEmpty(b []byte) interface{} {
	if len(b) == 0 {
		return nil
	}
	return b
}

// FetchByDeliveryTag returns ciphertext rows for blinded fetch (client proves possession out of band).
func FetchByDeliveryTag(ctx context.Context, db *sql.DB, deliveryTag []byte) (*sql.Rows, error) {
	const q = `SELECT id, ciphertext, encrypted_header, padded_len, expires_at
		FROM e2ee_messages WHERE delivery_tag = $1 AND expires_at > now() ORDER BY id ASC`
	return db.QueryContext(ctx, q, deliveryTag)
}

// MarkDelivered deletes or moves to archive — here delete for minimal retention.
func MarkDelivered(ctx context.Context, db *sql.DB, messageID int64) error {
	const q = `DELETE FROM e2ee_messages WHERE id = $1`
	res, err := db.ExecContext(ctx, q, messageID)
	if err != nil {
		return err
	}
	n, err := res.RowsAffected()
	if err != nil {
		return err
	}
	if n == 0 {
		return errors.New("e2ee: no row deleted")
	}
	return nil
}
