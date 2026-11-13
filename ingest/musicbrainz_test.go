package main

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestMusicBrainzAcceptsAKnownRecording(t *testing.T) {
	fake := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasPrefix(r.URL.Path, "/recording/") {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		if r.Header.Get("User-Agent") == "" {
			t.Error("missing User-Agent")
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"recordings":[
			{"title":"Golden Hour","length":214000,"artist-credit":[{"name":"Mira Sol"}]}
		]}`))
	}))
	defer fake.Close()

	mb := NewMusicBrainz(fake.URL, "sonora-test/1.0")
	err := mb.Verify(context.Background(), Metadata{Title: "Golden Hour", Artist: "Mira Sol", DurationMs: 214000})
	if err != nil {
		t.Fatal(err)
	}
}

func TestMusicBrainzRefusesAnUnknownRecording(t *testing.T) {
	fake := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"recordings":[]}`))
	}))
	defer fake.Close()

	mb := NewMusicBrainz(fake.URL, "sonora-test/1.0")
	err := mb.Verify(context.Background(), Metadata{Title: "Made Up", Artist: "Nobody", DurationMs: 180000})
	if err == nil {
		t.Fatal("unknown recording accepted")
	}
}

func TestMusicBrainzRefusesAWrongDuration(t *testing.T) {
	fake := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"recordings":[
			{"title":"Golden Hour","length":214000,"artist-credit":[{"name":"Mira Sol"}]}
		]}`))
	}))
	defer fake.Close()

	mb := NewMusicBrainz(fake.URL, "sonora-test/1.0")
	err := mb.Verify(context.Background(), Metadata{Title: "Golden Hour", Artist: "Mira Sol", DurationMs: 100000})
	if err == nil {
		t.Fatal("wrong duration accepted")
	}
}
