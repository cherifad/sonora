package main

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"sort"
	"strconv"
	"strings"
	"time"
)

// Fingerprinter proves that the audio really is the recording the tags claim: fpcalc
// (Chromaprint) reads the file, AcoustID maps the fingerprint to known recordings, and the
// names are compared. The MusicBrainz check only proves that *some* recording has these tags;
// this one proves that these bytes are it.
//
// It needs two things a host may not have: the fpcalc binary and an AcoustID API key. Without
// either it is off, and an upload is judged on its tags alone, as before.
type Fingerprinter struct {
	fpcalc    string
	clientKey string
	base      string
	minScore  float64
	http      *http.Client
	log       *Logger
}

// The confidence below which AcoustID's answer is not worth acting on.
const defaultFingerprintScore = 0.6
const contradictingScore = 0.9

func NewFingerprinter(log *Logger) *Fingerprinter {
	path, _ := exec.LookPath("fpcalc")
	return &Fingerprinter{
		fpcalc:    path,
		clientKey: strings.TrimSpace(os.Getenv("ACOUSTID_API_KEY")),
		base:      env("ACOUSTID_URL", "https://api.acoustid.org/v2"),
		minScore:  envFloat("ACOUSTID_MIN_SCORE", defaultFingerprintScore),
		http:      &http.Client{Timeout: 15 * time.Second},
		log:       log,
	}
}

func (f *Fingerprinter) Enabled() bool {
	return f != nil && f.clientKey != "" && f.fpcalc != ""
}

// Why says what stops the check, for the startup line.
func (f *Fingerprinter) Why() string {
	switch {
	case f == nil || f.clientKey == "":
		return "no ACOUSTID_API_KEY"
	case f.fpcalc == "":
		return "fpcalc not installed"
	default:
		return "on"
	}
}

type acousticRecording struct {
	Title   string `json:"title"`
	Artists []struct {
		Name string `json:"name"`
	} `json:"artists"`
}

type acoustIDResult struct {
	Score      float64             `json:"score"`
	Recordings []acousticRecording `json:"recordings"`
}

// fingerprint runs fpcalc and returns the duration in seconds and the fingerprint string.
func (f *Fingerprinter) fingerprint(ctx context.Context, path string) (int, string, error) {
	cmd := exec.CommandContext(ctx, f.fpcalc, "-json", "-length", "120", path)
	out, err := cmd.Output()
	if err != nil {
		return 0, "", fmt.Errorf("fpcalc: %w", err)
	}
	var parsed struct {
		Duration    float64 `json:"duration"`
		Fingerprint string  `json:"fingerprint"`
	}
	if err := json.Unmarshal(out, &parsed); err != nil {
		return 0, "", fmt.Errorf("fpcalc output: %w", err)
	}
	if parsed.Fingerprint == "" {
		return 0, "", fmt.Errorf("fpcalc returned no fingerprint")
	}
	return int(parsed.Duration), parsed.Fingerprint, nil
}

func (f *Fingerprinter) lookup(ctx context.Context, duration int, fingerprint string) ([]acoustIDResult, error) {
	query := url.Values{}
	query.Set("client", f.clientKey)
	query.Set("format", "json")
	query.Set("meta", "recordings")
	query.Set("duration", strconv.Itoa(duration))
	query.Set("fingerprint", fingerprint)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		strings.TrimRight(f.base, "/")+"/lookup", strings.NewReader(query.Encode()))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	resp, err := f.http.Do(req)
	if err != nil {
		return nil, fmt.Errorf("AcoustID unavailable: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusTooManyRequests {
		return nil, fmt.Errorf("AcoustID rate limit")
	}
	if resp.StatusCode >= 300 {
		return nil, fmt.Errorf("AcoustID HTTP %d", resp.StatusCode)
	}
	var body struct {
		Status  string           `json:"status"`
		Error   map[string]any   `json:"error"`
		Results []acoustIDResult `json:"results"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return nil, fmt.Errorf("AcoustID response: %w", err)
	}
	if body.Status != "ok" {
		return nil, fmt.Errorf("AcoustID: %v", body.Error)
	}
	return body.Results, nil
}

// Verify is nil when the audio matches the tags, when AcoustID has no confident answer, or
// when the check cannot run. It is an error only when AcoustID is sure and the names disagree:
// a wrong file in the library is worse than a review.
func (f *Fingerprinter) Verify(ctx context.Context, path string, meta Metadata) error {
	if !f.Enabled() {
		return nil
	}
	ctx, cancel := context.WithTimeout(ctx, 40*time.Second)
	defer cancel()

	duration, print, err := f.fingerprint(ctx, path)
	if err != nil {
		f.log.Warn("fingerprint failed", "detail", err)
		return nil
	}
	results, err := f.lookup(ctx, duration, print)
	if err != nil {
		f.log.Warn("fingerprint lookup failed", "detail", err)
		return nil
	}

	best, matched := pickRecording(results, meta, f.minScore)
	if matched {
		f.log.Info("fingerprint matches the tags", "title", best.recording.Title,
			"artist", artistNames(best.recording), "score", fmt.Sprintf("%.2f", best.result.Score))
		return nil
	}
	if best.result.Score >= contradictingScore && best.recording.Title != "" {
		return fmt.Errorf("the audio is %q by %q, not %q by %q",
			best.recording.Title, artistNames(best.recording), meta.Title, meta.Artist)
	}
	f.log.Info("fingerprint found no confident match", "title", meta.Title, "artist", meta.Artist,
		"results", len(results))
	return nil
}

type candidate struct {
	result    acoustIDResult
	recording acousticRecording
}

// pickRecording keeps the highest-scoring recordings above the threshold and says whether one
// of them carries the tags the app sent. Pure, so the rule is tested without AcoustID.
func pickRecording(results []acoustIDResult, meta Metadata, minScore float64) (candidate, bool) {
	candidates := make([]candidate, 0, len(results))
	for _, result := range results {
		if result.Score < minScore {
			continue
		}
		for _, recording := range result.Recordings {
			candidates = append(candidates, candidate{result: result, recording: recording})
		}
	}
	sort.SliceStable(candidates, func(i, j int) bool {
		return candidates[i].result.Score > candidates[j].result.Score
	})
	for _, c := range candidates {
		if normalize(c.recording.Title) != meta.normalizedTitle() {
			continue
		}
		artist := meta.Artist
		if artist == "" {
			artist = meta.AlbumArtist
		}
		for _, credit := range c.recording.Artists {
			if artistLike(credit.Name, artist) {
				return c, true
			}
		}
	}
	if len(candidates) == 0 {
		return candidate{}, false
	}
	return candidates[0], false
}

func artistNames(recording acousticRecording) string {
	names := make([]string, 0, len(recording.Artists))
	for _, artist := range recording.Artists {
		if artist.Name != "" {
			names = append(names, artist.Name)
		}
	}
	return strings.Join(names, ", ")
}

func envFloat(key string, fallback float64) float64 {
	if raw := os.Getenv(key); raw != "" {
		if value, err := strconv.ParseFloat(raw, 64); err == nil && value > 0 && value <= 1 {
			return value
		}
	}
	return fallback
}
