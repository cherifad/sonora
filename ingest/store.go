package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// Metadata is what the app knows about a local file before uploading it.
type Metadata struct {
	Title       string `json:"title"`
	Artist      string `json:"artist"`
	Album       string `json:"album"`
	AlbumArtist string `json:"albumArtist"`
	Track       int    `json:"track"`
	Disc        int    `json:"disc"`
	Year        int    `json:"year"`
	ISRC        string `json:"isrc"`
	MBID        string `json:"mbid"`
	DurationMs  int64  `json:"durationMs"`
}

func (m Metadata) normalizedTitle() string { return normalize(m.Title) }
func (m Metadata) normalizedArtist() string {
	if m.AlbumArtist != "" {
		return normalize(m.AlbumArtist)
	}
	return normalize(m.Artist)
}

func normalize(s string) string {
	return strings.Join(strings.Fields(strings.ToLower(strings.TrimSpace(s))), " ")
}

// artistLike compares two artist names loosely: "A, B" and "A & B" should not fail the
// embedded-tag check, and MusicBrainz credits sometimes carry a longer name.
func artistLike(a, b string) bool {
	na, nb := normalize(a), normalize(b)
	if na == "" || nb == "" {
		return false
	}
	return na == nb || strings.Contains(na, nb) || strings.Contains(nb, na)
}

// Upload is one file being received. Status is one of:
// uploading, verifying, waiting_scan, done, duplicate, needs_review, failed.
type Upload struct {
	ID            string    `json:"id"`
	Filename      string    `json:"filename"`
	Size          int64     `json:"size"`
	SHA256        string    `json:"sha256"`
	Metadata      Metadata  `json:"metadata"`
	Offset        int64     `json:"offset"`
	Status        string    `json:"status"`
	Origin        string    `json:"origin,omitempty"` // new or duplicate
	Match         string    `json:"match,omitempty"`  // hash, isrc, mbid, metadata
	Path          string    `json:"path,omitempty"`
	ServerTrackID string    `json:"serverTrackId,omitempty"`
	Error         string    `json:"error,omitempty"`
	CreatedAt     time.Time `json:"createdAt"`
	UpdatedAt     time.Time `json:"updatedAt"`
}

// FileRecord is a file already accepted into the library.
type FileRecord struct {
	SHA256        string    `json:"sha256"`
	Path          string    `json:"path"`
	ServerTrackID string    `json:"serverTrackId,omitempty"`
	ISRC          string    `json:"isrc,omitempty"`
	MBID          string    `json:"mbid,omitempty"`
	Metadata      Metadata  `json:"metadata"`
	CreatedAt     time.Time `json:"createdAt"`
}

type state struct {
	Uploads map[string]*Upload     `json:"uploads"`
	Files   map[string]*FileRecord `json:"files"`
	ISRC    map[string]string      `json:"isrc"`
	MBID    map[string]string      `json:"mbid"`
}

// Store keeps the ingest state in one JSON file. A companion service, not a database:
// a few thousand tracks fit comfortably, and the file is written atomically.
type Store struct {
	mu    sync.Mutex
	path  string
	state state
}

func NewStore(path string) (*Store, error) {
	s := &Store{path: path, state: state{
		Uploads: map[string]*Upload{},
		Files:   map[string]*FileRecord{},
		ISRC:    map[string]string{},
		MBID:    map[string]string{},
	}}
	raw, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return s, nil
	}
	if err != nil {
		return nil, err
	}
	if err := json.Unmarshal(raw, &s.state); err != nil {
		return nil, err
	}
	if s.state.Uploads == nil {
		s.state.Uploads = map[string]*Upload{}
	}
	if s.state.Files == nil {
		s.state.Files = map[string]*FileRecord{}
	}
	if s.state.ISRC == nil {
		s.state.ISRC = map[string]string{}
	}
	if s.state.MBID == nil {
		s.state.MBID = map[string]string{}
	}
	return s, nil
}

// Counts is the size of the state, for the startup line: how much the service already knows.
func (s *Store) Counts() (files, uploads int) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.state.Files), len(s.state.Uploads)
}

func (s *Store) saveLocked() error {
	raw, err := json.MarshalIndent(s.state, "", "  ")
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(s.path), 0o755); err != nil {
		return err
	}
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, raw, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, s.path)
}

type MatchResult struct {
	Decision      string `json:"decision"` // new, duplicate, needs_review
	Match         string `json:"match,omitempty"`
	ServerTrackID string `json:"serverTrackId,omitempty"`
	Path          string `json:"path,omitempty"`
	Reason        string `json:"reason,omitempty"`
}

// Match decides before any byte is sent. Only exact identity (hash, ISRC, MBID) is a
// duplicate; a close metadata match is left for review and never uploaded automatically.
func (s *Store) Match(sha string, meta Metadata) MatchResult {
	s.mu.Lock()
	defer s.mu.Unlock()
	if sha != "" {
		if rec, ok := s.state.Files[sha]; ok {
			return MatchResult{Decision: "duplicate", Match: "hash", ServerTrackID: rec.ServerTrackID, Path: rec.Path}
		}
	}
	if meta.ISRC != "" {
		if hash, ok := s.state.ISRC[strings.ToUpper(meta.ISRC)]; ok {
			rec := s.state.Files[hash]
			return MatchResult{Decision: "duplicate", Match: "isrc", ServerTrackID: rec.ServerTrackID, Path: rec.Path}
		}
	}
	if meta.MBID != "" {
		if hash, ok := s.state.MBID[strings.ToLower(meta.MBID)]; ok {
			rec := s.state.Files[hash]
			return MatchResult{Decision: "duplicate", Match: "mbid", ServerTrackID: rec.ServerTrackID, Path: rec.Path}
		}
	}
	if meta.Title != "" && meta.Artist != "" {
		for _, rec := range s.state.Files {
			if rec.Metadata.normalizedTitle() == meta.normalizedTitle() &&
				rec.Metadata.normalizedArtist() == meta.normalizedArtist() &&
				durationClose(rec.Metadata.DurationMs, meta.DurationMs) {
				return MatchResult{Decision: "needs_review", Match: "metadata", Reason: "a close metadata match exists; not uploaded automatically"}
			}
		}
	}
	return MatchResult{Decision: "new"}
}

func durationClose(a, b int64) bool {
	if a <= 0 || b <= 0 {
		return false
	}
	d := a - b
	if d < 0 {
		d = -d
	}
	return d <= 2000
}

func (s *Store) CreateUpload(filename string, size int64, sha string, meta Metadata) (*Upload, MatchResult, error) {
	match := s.Match(sha, meta)
	if match.Decision == "duplicate" {
		return nil, match, nil
	}
	if match.Decision == "needs_review" {
		return nil, match, nil
	}
	id, err := randomID()
	if err != nil {
		return nil, match, err
	}
	now := time.Now().UTC()
	u := &Upload{
		ID: id, Filename: filename, Size: size, SHA256: strings.ToLower(sha), Metadata: meta,
		Status: "uploading", CreatedAt: now, UpdatedAt: now,
	}
	s.mu.Lock()
	s.state.Uploads[id] = u
	err = s.saveLocked()
	s.mu.Unlock()
	return u, match, err
}

func (s *Store) Upload(id string) (*Upload, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	u, ok := s.state.Uploads[id]
	return u, ok
}

func (s *Store) SetStatus(id, status, message string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if u, ok := s.state.Uploads[id]; ok {
		u.Status = status
		u.Error = message
		u.UpdatedAt = time.Now().UTC()
		_ = s.saveLocked()
	}
}

func (s *Store) SetOffset(id string, offset int64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if u, ok := s.state.Uploads[id]; ok {
		u.Offset = offset
		u.UpdatedAt = time.Now().UTC()
		_ = s.saveLocked()
	}
}

func (s *Store) SetPath(id, path string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if u, ok := s.state.Uploads[id]; ok {
		u.Path = path
		u.UpdatedAt = time.Now().UTC()
		_ = s.saveLocked()
	}
}

func (s *Store) SetServerTrackID(id, trackID string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if u, ok := s.state.Uploads[id]; ok {
		u.ServerTrackID = trackID
		u.Status = "done"
		u.Origin = "new"
		u.UpdatedAt = time.Now().UTC()
		_ = s.saveLocked()
	}
}

func (s *Store) SetFileServerTrackID(sha, trackID string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if rec, ok := s.state.Files[sha]; ok {
		rec.ServerTrackID = trackID
		_ = s.saveLocked()
	}
}

func (s *Store) AddFile(rec *FileRecord) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.state.Files[rec.SHA256] = rec
	if rec.ISRC != "" {
		s.state.ISRC[strings.ToUpper(rec.ISRC)] = rec.SHA256
	}
	if rec.MBID != "" {
		s.state.MBID[strings.ToLower(rec.MBID)] = rec.SHA256
	}
	_ = s.saveLocked()
}

func (s *Store) RemoveUpload(id string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	delete(s.state.Uploads, id)
	_ = s.saveLocked()
}

// RemoveFile forgets an uploaded file (and its ISRC/MBID shortcuts), so a later upload of
// the same bytes is not reported as a duplicate of something that no longer exists.
func (s *Store) RemoveFile(sha string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	rec, ok := s.state.Files[sha]
	if !ok {
		return
	}
	delete(s.state.Files, sha)
	if rec.ISRC != "" {
		delete(s.state.ISRC, strings.ToUpper(rec.ISRC))
	}
	if rec.MBID != "" {
		delete(s.state.MBID, strings.ToLower(rec.MBID))
	}
	_ = s.saveLocked()
}

func randomID() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return hex.EncodeToString(b[:]), nil
}
