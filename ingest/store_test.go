package main

import (
	"path/filepath"
	"testing"
)

func TestMatchHashThenISRCThenMBID(t *testing.T) {
	store, err := NewStore(filepath.Join(t.TempDir(), "state.json"))
	if err != nil {
		t.Fatal(err)
	}
	meta := Metadata{Title: "Golden Hour", Artist: "Mira Sol", Album: "Golden Hour", DurationMs: 214000, ISRC: "USABC1234567", MBID: "abc"}
	store.AddFile(&FileRecord{SHA256: "sha1", Path: "Mira Sol/Golden Hour/01.flac", ServerTrackID: "42", Metadata: meta, ISRC: meta.ISRC, MBID: meta.MBID})

	if got := store.Match("sha1", meta); got.Decision != "duplicate" || got.Match != "hash" || got.ServerTrackID != "42" {
		t.Fatalf("hash match = %+v", got)
	}
	if got := store.Match("other", Metadata{ISRC: "USABC1234567"}); got.Decision != "duplicate" || got.Match != "isrc" {
		t.Fatalf("isrc match = %+v", got)
	}
	if got := store.Match("other", Metadata{MBID: "abc"}); got.Decision != "duplicate" || got.Match != "mbid" {
		t.Fatalf("mbid match = %+v", got)
	}
}

func TestCloseMetadataIsReviewNotDuplicate(t *testing.T) {
	store, err := NewStore(filepath.Join(t.TempDir(), "state.json"))
	if err != nil {
		t.Fatal(err)
	}
	meta := Metadata{Title: "Golden Hour", Artist: "Mira Sol", DurationMs: 214000}
	store.AddFile(&FileRecord{SHA256: "sha1", Path: "a.flac", Metadata: meta})

	got := store.Match("different", Metadata{Title: "golden  hour", Artist: "MIRA SOL", DurationMs: 215000})
	if got.Decision != "needs_review" {
		t.Fatalf("close metadata = %+v, want needs_review", got)
	}
}

func TestNewMetadataIsNew(t *testing.T) {
	store, err := NewStore(filepath.Join(t.TempDir(), "state.json"))
	if err != nil {
		t.Fatal(err)
	}
	if got := store.Match("sha", Metadata{Title: "Other", Artist: "Someone", DurationMs: 180000}); got.Decision != "new" {
		t.Fatalf("new match = %+v", got)
	}
}
