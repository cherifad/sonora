package main

import (
	"testing"
	"time"
)

func TestTokenIssueVerifyAndTamper(t *testing.T) {
	issuer := NewTokenIssuer("secret", time.Hour)
	token, expires, err := issuer.Issue("me", "server-1")
	if err != nil {
		t.Fatal(err)
	}
	if !expires.After(time.Now()) {
		t.Fatal("token already expired")
	}
	claims, err := issuer.Verify(token)
	if err != nil {
		t.Fatal(err)
	}
	if claims.Sub != "me" || claims.Srv != "server-1" || claims.Scope != "upload" {
		t.Fatalf("claims = %+v", claims)
	}
	if _, err := issuer.Verify(token + "x"); err == nil {
		t.Fatal("tampered token accepted")
	}
}

func TestExpiredTokenIsRefused(t *testing.T) {
	issuer := NewTokenIssuer("secret", -time.Minute)
	token, _, err := issuer.Issue("me", "server-1")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := issuer.Verify(token); err == nil {
		t.Fatal("expired token accepted")
	}
}
