package main

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestPickRecordingAcceptsATagMatch(t *testing.T) {
	results := []acoustIDResult{
		{Score: 0.92, Recordings: []acousticRecording{{Title: "Other Song", Artists: []struct {
			Name string `json:"name"`
		}{{Name: "Other"}}}}},
		{Score: 0.88, Recordings: []acousticRecording{{Title: "Some Song", Artists: []struct {
			Name string `json:"name"`
		}{{Name: "Someone"}}}}},
	}
	if _, ok := pickRecording(results, Metadata{Title: "Some Song", Artist: "Someone"}, 0.6); !ok {
		t.Fatal("a recording with the same title and artist must match")
	}
}

func TestPickRecordingLoosensTheArtistName(t *testing.T) {
	results := []acoustIDResult{{Score: 0.8, Recordings: []acousticRecording{{
		Title: "Some Song",
		Artists: []struct {
			Name string `json:"name"`
		}{{Name: "Someone & Co"}},
	}}}}
	if _, ok := pickRecording(results, Metadata{Title: "Some Song", Artist: "Someone"}, 0.6); !ok {
		t.Fatal(`"Someone & Co" and "Someone" are the same artist for the tag check`)
	}
}

func TestPickRecordingIgnoresLowScores(t *testing.T) {
	results := []acoustIDResult{{Score: 0.2, Recordings: []acousticRecording{{
		Title: "Some Song",
		Artists: []struct {
			Name string `json:"name"`
		}{{Name: "Someone"}},
	}}}}
	if _, ok := pickRecording(results, Metadata{Title: "Some Song", Artist: "Someone"}, 0.6); ok {
		t.Fatal("an uncertain answer must not count as a match")
	}
}

// fakeFPCalc writes a stand-in for the Chromaprint binary: Verify only needs its JSON.
func fakeFPCalc(t *testing.T, body string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "fpcalc")
	script := "#!/bin/sh\nprintf '%s'\n"
	if err := os.WriteFile(path, []byte(strings.Replace(script, "%s", body, 1)), 0o755); err != nil {
		t.Fatal(err)
	}
	return path
}

func acoustID(t *testing.T, body string, status int) *httptest.Server {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/lookup" {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		if err := r.ParseForm(); err != nil {
			t.Errorf("not a form: %v", err)
		}
		if r.Form.Get("fingerprint") == "" || r.Form.Get("client") == "" {
			t.Error("the lookup must carry the fingerprint and the key")
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(status)
		_, _ = io.WriteString(w, body)
	}))
	t.Cleanup(server.Close)
	return server
}

func fingerprinter(t *testing.T, calc string, server *httptest.Server) *Fingerprinter {
	t.Helper()
	return &Fingerprinter{
		fpcalc:    calc,
		clientKey: "test-key",
		base:      server.URL,
		minScore:  0.6,
		http:      server.Client(),
		log:       NewLogger(io.Discard, LevelError, false),
	}
}

func TestVerifyPassesWhenTheAudioMatchesTheTags(t *testing.T) {
	calc := fakeFPCalc(t, `{"duration": 200.4, "fingerprint": "AQADtEmi"}`)
	server := acoustID(t, `{"status":"ok","results":[{"score":0.95,"recordings":[{"title":"Some Song","artists":[{"name":"Someone"}]}]}]}`, 200)
	f := fingerprinter(t, calc, server)

	if err := f.Verify(context.Background(), "/tmp/song.flac", Metadata{Title: "Some Song", Artist: "Someone"}); err != nil {
		t.Fatalf("matching audio rejected: %v", err)
	}
}

func TestVerifyRejectsACertainlyDifferentRecording(t *testing.T) {
	calc := fakeFPCalc(t, `{"duration": 200.4, "fingerprint": "AQADtEmi"}`)
	server := acoustID(t, `{"status":"ok","results":[{"score":0.97,"recordings":[{"title":"Another Song","artists":[{"name":"Someone Else"}]}]}]}`, 200)
	f := fingerprinter(t, calc, server)

	err := f.Verify(context.Background(), "/tmp/song.flac", Metadata{Title: "Some Song", Artist: "Someone"})
	if err == nil {
		t.Fatal("a confident different recording must be rejected")
	}
	if !strings.Contains(err.Error(), "Another Song") {
		t.Fatalf("the reason should name the recording: %v", err)
	}
}

func TestVerifyToleratesAnUncertainOrBrokenAcoustID(t *testing.T) {
	calc := fakeFPCalc(t, `{"duration": 200.4, "fingerprint": "AQADtEmi"}`)

	uncertain := acoustID(t, `{"status":"ok","results":[{"score":0.3,"recordings":[{"title":"Another Song","artists":[{"name":"Someone Else"}]}]}]}`, 200)
	if err := fingerprinter(t, calc, uncertain).Verify(context.Background(), "/x", Metadata{Title: "Some Song", Artist: "Someone"}); err != nil {
		t.Fatalf("an uncertain answer must not reject: %v", err)
	}

	broken := acoustID(t, `{"status":"error","error":{"code":4,"message":"invalid key"}}`, 200)
	if err := fingerprinter(t, calc, broken).Verify(context.Background(), "/x", Metadata{Title: "Some Song", Artist: "Someone"}); err != nil {
		t.Fatalf("an AcoustID error must not reject the upload: %v", err)
	}
}

func TestVerifyIsOffWithoutAKeyOrBinary(t *testing.T) {
	if (&Fingerprinter{clientKey: "k"}).Enabled() {
		t.Fatal("no fpcalc: off")
	}
	if (&Fingerprinter{fpcalc: "/usr/bin/fpcalc"}).Enabled() {
		t.Fatal("no key: off")
	}
	if !(&Fingerprinter{fpcalc: "/x", clientKey: "k"}).Enabled() {
		t.Fatal("both: on")
	}
	if err := (&Fingerprinter{}).Verify(context.Background(), "/x", Metadata{}); err != nil {
		t.Fatalf("a disabled check must be silent: %v", err)
	}
}
