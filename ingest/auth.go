package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"strings"
	"sync"
	"time"
)

type Claims struct {
	Sub   string `json:"sub"`
	Srv   string `json:"srv"`
	Scope string `json:"scope"`
	Iat   int64  `json:"iat"`
	Exp   int64  `json:"exp"`
}

// TokenIssuer signs the short-lived upload tokens handed to the app after it proves the
// Subsonic credentials. The static TOKEN is still accepted for curl/admin.
type TokenIssuer struct {
	secret []byte
	ttl    time.Duration
}

func NewTokenIssuer(secret string, ttl time.Duration) *TokenIssuer {
	sum := sha256.Sum256([]byte("sonora-ingest:" + secret))
	return &TokenIssuer{secret: sum[:], ttl: ttl}
}

func (i *TokenIssuer) Issue(user, server string) (string, time.Time, error) {
	now := time.Now().UTC()
	expires := now.Add(i.ttl)
	payload, err := json.Marshal(Claims{
		Sub: user, Srv: server, Scope: "upload", Iat: now.Unix(), Exp: expires.Unix(),
	})
	if err != nil {
		return "", time.Time{}, err
	}
	body := base64.RawURLEncoding.EncodeToString(payload)
	return body + "." + i.sign(body), expires, nil
}

func (i *TokenIssuer) Verify(token string) (*Claims, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 2 {
		return nil, errors.New("malformed token")
	}
	if !hmac.Equal([]byte(i.sign(parts[0])), []byte(parts[1])) {
		return nil, errors.New("bad signature")
	}
	raw, err := base64.RawURLEncoding.DecodeString(parts[0])
	if err != nil {
		return nil, errors.New("bad payload")
	}
	var claims Claims
	if err := json.Unmarshal(raw, &claims); err != nil {
		return nil, errors.New("bad claims")
	}
	if claims.Exp <= time.Now().Unix() {
		return nil, errors.New("expired token")
	}
	return &claims, nil
}

func (i *TokenIssuer) sign(payload string) string {
	mac := hmac.New(sha256.New, i.secret)
	mac.Write([]byte(payload))
	return base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}

func serverID(cfg Config) string {
	base := cfg.SubsonicURL
	if base == "" {
		base = cfg.ScanURL
	}
	sum := sha256.Sum256([]byte(base))
	return hex.EncodeToString(sum[:8])
}

// rateLimiter is deliberately small: the exchange endpoint is the only unauthenticated
// one, and a personal server does not need a distributed limiter.
type rateLimiter struct {
	mu     sync.Mutex
	hits   map[string][]time.Time
	limit  int
	window time.Duration
}

func newRateLimiter(limit int, window time.Duration) *rateLimiter {
	return &rateLimiter{hits: map[string][]time.Time{}, limit: limit, window: window}
}

func (l *rateLimiter) allow(key string) bool {
	now := time.Now()
	l.mu.Lock()
	defer l.mu.Unlock()
	cutoff := now.Add(-l.window)
	kept := l.hits[key][:0]
	for _, at := range l.hits[key] {
		if at.After(cutoff) {
			kept = append(kept, at)
		}
	}
	if len(kept) >= l.limit {
		l.hits[key] = kept
		return false
	}
	l.hits[key] = append(kept, now)
	return true
}
