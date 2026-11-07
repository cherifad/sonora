package main

import (
	"fmt"
	"path/filepath"
	"regexp"
	"strings"
)

var illegal = regexp.MustCompile(`[\\/:*?"<>|\x00-\x1f]`)

// sanitizeComponent makes one path component safe: no separators, no traversal, no control
// characters, a bounded length, and never empty.
func sanitizeComponent(s string) string {
	s = strings.TrimSpace(s)
	s = illegal.ReplaceAllString(s, "_")
	s = strings.Join(strings.Fields(s), " ")
	s = strings.Trim(s, ". ")
	if s == "" || s == "." || s == ".." {
		return "Unknown"
	}
	r := []rune(s)
	if len(r) > 120 {
		s = string(r[:120])
	}
	return s
}

// extensionOf accepts the audio formats a music library should hold.
func extensionOf(filename string) string {
	switch ext := strings.ToLower(filepath.Ext(filename)); ext {
	case ".flac", ".mp3", ".m4a", ".aac", ".ogg", ".opus", ".wav":
		return ext
	default:
		return ""
	}
}

// targetPath is Artist/Album/NN - Title.ext, falling back to Artist/Title.ext.
// Every component is sanitized; the caller still checks the final path is under the library.
func targetPath(meta Metadata, ext string) string {
	artist := sanitizeComponent(meta.AlbumArtist)
	if artist == "Unknown" {
		artist = sanitizeComponent(meta.Artist)
	}
	album := sanitizeComponent(meta.Album)
	title := sanitizeComponent(meta.Title)
	name := title
	if meta.Track > 0 {
		name = fmt.Sprintf("%02d - %s", meta.Track, title)
	}
	if album == "Unknown" {
		return filepath.Join(artist, name+ext)
	}
	return filepath.Join(artist, album, name+ext)
}
