package main

import (
	"context"
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"math/rand"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// Scanner triggers the server's library scan and finds the track once indexed.
type Scanner interface {
	Trigger(ctx context.Context) error
	Verify(ctx context.Context, meta Metadata) (string, error)
}

// SubsonicScanner talks to Navidrome, Jellyfin+Subfin or any Subsonic server.
type SubsonicScanner struct {
	base string
	user string
	pass string
	http *http.Client
}

func (s *SubsonicScanner) endpoint(method string, params, auth url.Values) string {
	q := url.Values{}
	q.Set("v", "1.16.1")
	q.Set("c", "sonora-ingest")
	q.Set("f", "json")
	if len(auth) == 0 {
		salt := randomSalt()
		sum := md5.Sum([]byte(s.pass + salt))
		q.Set("u", s.user)
		q.Set("t", hex.EncodeToString(sum[:]))
		q.Set("s", salt)
	} else {
		for key, values := range auth {
			for _, value := range values {
				q.Add(key, value)
			}
		}
	}
	for key, values := range params {
		for _, value := range values {
			q.Add(key, value)
		}
	}
	return strings.TrimRight(s.base, "/") + "/rest/" + method + ".view?" + q.Encode()
}

type subsonicEnvelope struct {
	Response struct {
		Status string `json:"status"`
		Error  *struct {
			Code    int    `json:"code"`
			Message string `json:"message"`
		} `json:"error"`
		SearchResult3 struct {
			Song []struct {
				ID       string `json:"id"`
				Title    string `json:"title"`
				Artist   string `json:"artist"`
				Album    string `json:"album"`
				Duration int    `json:"duration"`
			} `json:"song"`
		} `json:"searchResult3"`
	} `json:"subsonic-response"`
}

func (s *SubsonicScanner) getWith(ctx context.Context, method string, params, auth url.Values) (*subsonicEnvelope, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, s.endpoint(method, params, auth), nil)
	if err != nil {
		return nil, err
	}
	resp, err := s.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("subsonic HTTP %d", resp.StatusCode)
	}
	var env subsonicEnvelope
	if err := json.NewDecoder(resp.Body).Decode(&env); err != nil {
		return nil, err
	}
	if env.Response.Status != "ok" {
		if env.Response.Error != nil {
			return nil, fmt.Errorf("subsonic error %d: %s", env.Response.Error.Code, env.Response.Error.Message)
		}
		return nil, fmt.Errorf("subsonic status %s", env.Response.Status)
	}
	return &env, nil
}

func (s *SubsonicScanner) get(ctx context.Context, method string, params url.Values) (*subsonicEnvelope, error) {
	return s.getWith(ctx, method, params, nil)
}

// Ping proves a user's credentials: the app sends its own u/t/s (or an API key) and the
// service only sees whether the server accepts them.
func (s *SubsonicScanner) Ping(ctx context.Context, auth url.Values) error {
	_, err := s.getWith(ctx, "ping", url.Values{}, auth)
	return err
}

func (s *SubsonicScanner) Trigger(ctx context.Context) error {
	_, err := s.get(ctx, "startScan", url.Values{})
	return err
}

func (s *SubsonicScanner) Verify(ctx context.Context, meta Metadata) (string, error) {
	params := url.Values{}
	params.Set("query", meta.Title)
	params.Set("songCount", "30")
	env, err := s.get(ctx, "search3", params)
	if err != nil {
		return "", err
	}
	for _, song := range env.Response.SearchResult3.Song {
		if normalize(song.Title) != meta.normalizedTitle() {
			continue
		}
		if meta.Artist != "" && normalize(song.Artist) != meta.normalizedArtist() {
			continue
		}
		if meta.DurationMs > 0 && !durationClose(int64(song.Duration)*1000, meta.DurationMs) {
			continue
		}
		return song.ID, nil
	}
	return "", fmt.Errorf("track not indexed yet")
}

// JellyfinScanner triggers Jellyfin's own library refresh; verification stays on the
// Subsonic endpoint (Subfin) because that is what the app uses to read the library.
type JellyfinScanner struct {
	base   string
	apiKey string
	verify *SubsonicScanner
	http   *http.Client
}

func (j *JellyfinScanner) Trigger(ctx context.Context) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, strings.TrimRight(j.base, "/")+"/Library/Refresh", nil)
	if err != nil {
		return err
	}
	req.Header.Set("X-Emby-Token", j.apiKey)
	resp, err := j.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return fmt.Errorf("jellyfin HTTP %d", resp.StatusCode)
	}
	return nil
}

func (j *JellyfinScanner) Verify(ctx context.Context, meta Metadata) (string, error) {
	if j.verify == nil {
		return "", fmt.Errorf("no Subsonic endpoint for verification")
	}
	return j.verify.Verify(ctx, meta)
}

func NewScanner(cfg Config) Scanner {
	if cfg.ScanURL == "" {
		return nil
	}
	client := &http.Client{Timeout: 20 * time.Second}
	verifyBase := cfg.SubsonicURL
	if verifyBase == "" {
		verifyBase = cfg.ScanURL
	}
	if cfg.ServerType == "jellyfin" {
		return &JellyfinScanner{
			base: cfg.ScanURL, apiKey: cfg.ScanAPIKey, http: client,
			verify: &SubsonicScanner{base: verifyBase, user: cfg.ScanUser, pass: cfg.ScanPassword, http: client},
		}
	}
	return &SubsonicScanner{base: cfg.ScanURL, user: cfg.ScanUser, pass: cfg.ScanPassword, http: client}
}

func randomSalt() string {
	const alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	b := make([]byte, 12)
	for i := range b {
		b[i] = alphabet[rand.Intn(len(alphabet))]
	}
	return string(b)
}
