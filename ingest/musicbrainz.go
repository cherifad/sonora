package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

// MusicBrainz checks that a recording really exists with the artist and title the app sent.
// It does not listen to the audio: it proves the tags are plausible, not that the file is
// the recording it claims to be (a fingerprint would).
type MusicBrainz struct {
	base      string
	userAgent string
	http      *http.Client
	mu        sync.Mutex
	last      time.Time
	log       *Logger
}

func NewMusicBrainz(base, userAgent string) *MusicBrainz {
	if base == "" {
		base = "https://musicbrainz.org/ws/2"
	}
	if userAgent == "" {
		userAgent = "sonora-ingest/1.0 (https://github.com/cherifad/sonora)"
	}
	return &MusicBrainz{
		base:      strings.TrimRight(base, "/"),
		userAgent: userAgent,
		http:      &http.Client{Timeout: 12 * time.Second},
	}
}

type mbRecording struct {
	Title        string `json:"title"`
	Length       int64  `json:"length"`
	ArtistCredit []struct {
		Name string `json:"name"`
	} `json:"artist-credit"`
}

// Verify returns nil when MusicBrainz knows a recording with this title and artist (and a
// length within 5 s when both sides have one). Any other outcome is an error: the caller
// turns it into a needs_review decision.
func (m *MusicBrainz) Verify(ctx context.Context, meta Metadata) error {
	if meta.Title == "" || meta.Artist == "" {
		return fmt.Errorf("title and artist are required")
	}
	if err := m.wait(ctx); err != nil {
		return err
	}
	m.log.Debug("musicbrainz query", "title", meta.Title, "artist", meta.Artist)
	query := fmt.Sprintf(`recording:"%s" AND artist:"%s"`, escapeLucene(meta.Title), escapeLucene(meta.Artist))
	endpoint := m.base + "/recording/?query=" + url.QueryEscape(query) + "&fmt=json&limit=5"
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", m.userAgent)
	req.Header.Set("Accept", "application/json")
	resp, err := m.http.Do(req)
	if err != nil {
		m.log.Warn("musicbrainz unreachable", "detail", err)
		return fmt.Errorf("MusicBrainz unavailable: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusTooManyRequests {
		m.log.Warn("musicbrainz rate limit", "hint", "the next upload may be held for review")
		return fmt.Errorf("MusicBrainz rate limit; try again later")
	}
	if resp.StatusCode >= 300 {
		m.log.Warn("musicbrainz error", "status", resp.StatusCode)
		return fmt.Errorf("MusicBrainz HTTP %d", resp.StatusCode)
	}
	var out struct {
		Recordings []mbRecording `json:"recordings"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return fmt.Errorf("MusicBrainz response: %w", err)
	}
	for _, rec := range out.Recordings {
		if normalize(rec.Title) != meta.normalizedTitle() {
			continue
		}
		if !artistMatches(rec.ArtistCredit, meta.Artist) {
			continue
		}
		if meta.DurationMs > 0 && rec.Length > 0 && abs64(rec.Length-meta.DurationMs) > 5000 {
			continue
		}
		m.log.Debug("musicbrainz match", "title", rec.Title, "lengthMs", rec.Length)
		return nil
	}
	m.log.Info("musicbrainz does not know this recording", "title", meta.Title, "artist", meta.Artist,
		"recordings", len(out.Recordings))
	return fmt.Errorf("no MusicBrainz match for %q by %q", meta.Title, meta.Artist)
}

// wait keeps the one request per second MusicBrainz asks for; the mutex serialises the
// service's own callers so two uploads cannot race past the limit.
func (m *MusicBrainz) wait(ctx context.Context) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if wait := time.Second - time.Since(m.last); wait > 0 {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(wait):
		}
	}
	m.last = time.Now()
	return nil
}

func escapeLucene(s string) string {
	s = strings.ReplaceAll(s, `\`, `\\`)
	return strings.ReplaceAll(s, `"`, `\"`)
}

func artistMatches(credit []struct {
	Name string `json:"name"`
}, artist string) bool {
	for _, a := range credit {
		if artistLike(a.Name, artist) {
			return true
		}
	}
	return false
}

func abs64(v int64) int64 {
	if v < 0 {
		return -v
	}
	return v
}
