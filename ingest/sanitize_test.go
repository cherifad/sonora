package main

import (
	"strings"
	"testing"
)

func TestSanitizeComponentStripsTraversalAndSeparators(t *testing.T) {
	for _, input := range []string{"../../etc/passwd", `a/b\c`, "..", "  ", "a\x00b"} {
		got := sanitizeComponent(input)
		if strings.ContainsAny(got, `/\`) || got == ".." || got == "." {
			t.Fatalf("sanitizeComponent(%q) = %q", input, got)
		}
	}
}

func TestTargetPathKeepsArtistAndAlbum(t *testing.T) {
	got := targetPath(Metadata{
		Title: "Golden Hour", Artist: "Mira Sol", Album: "Golden Hour", Track: 1,
	}, ".flac")
	want := "Mira Sol/Golden Hour/01 - Golden Hour.flac"
	if got != want {
		t.Fatalf("targetPath = %q, want %q", got, want)
	}
}

func TestExtensionAllowList(t *testing.T) {
	for _, ext := range []string{".flac", ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav"} {
		if extensionOf("track"+ext) != ext {
			t.Fatalf("extensionOf(%q) rejected", ext)
		}
	}
	for _, ext := range []string{".amr", ".3gp", ".txt", ""} {
		if extensionOf("track"+ext) != "" {
			t.Fatalf("extensionOf(%q) accepted", ext)
		}
	}
}
