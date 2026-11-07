package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
)

type fakeScanner struct {
	triggers int
	trackID  string
}

func (f *fakeScanner) Trigger(context.Context) error { f.triggers++; return nil }
func (f *fakeScanner) Verify(context.Context, Metadata) (string, error) {
	if f.trackID == "" {
		return "", fmt.Errorf("not indexed yet")
	}
	return f.trackID, nil
}

func newTestServer(t *testing.T) (*Server, *fakeScanner) {
	t.Helper()
	dir := t.TempDir()
	store, err := NewStore(filepath.Join(dir, "state.json"))
	if err != nil {
		t.Fatal(err)
	}
	scanner := &fakeScanner{}
	return &Server{
		cfg: Config{
			Token: "secret", LibraryPath: filepath.Join(dir, "music"),
			IncomingPath: filepath.Join(dir, "incoming"), MaxUploadBytes: 1 << 30,
			SkipAudioValidation: true,
		},
		store: store, scanner: scanner,
	}, scanner
}

func call(server *Server, method, path string, body []byte, headers map[string]string, authorized bool) *httptest.ResponseRecorder {
	req := httptest.NewRequest(method, path, bytes.NewReader(body))
	if authorized {
		req.Header.Set("Authorization", "Bearer secret")
	}
	for key, value := range headers {
		req.Header.Set(key, value)
	}
	rec := httptest.NewRecorder()
	mux := http.NewServeMux()
	mux.HandleFunc("/library/match", server.auth(server.handleMatch))
	mux.HandleFunc("/uploads", server.auth(server.handleUploads))
	mux.HandleFunc("/uploads/", server.auth(server.handleUploads))
	mux.ServeHTTP(rec, req)
	return rec
}

func sha256Hex(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])
}

func TestUploadChunksDuplicateAndVerify(t *testing.T) {
	server, scanner := newTestServer(t)
	meta := `{"title":"Golden Hour","artist":"Mira Sol","album":"Golden Hour","track":1,"durationMs":214000}`
	hash := sha256Hex("hello")
	create := fmt.Sprintf(`{"filename":"01 - Golden Hour.flac","size":5,"sha256":"%s","metadata":%s}`, hash, meta)

	rec := call(server, http.MethodPost, "/uploads", []byte(create), nil, true)
	if rec.Code != http.StatusCreated {
		t.Fatalf("create: %d %s", rec.Code, rec.Body.String())
	}
	var created struct {
		UploadID string `json:"uploadId"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &created); err != nil {
		t.Fatal(err)
	}
	if created.UploadID == "" {
		t.Fatal("empty upload id")
	}

	rec = call(server, http.MethodPatch, "/uploads/"+created.UploadID, []byte("hel"),
		map[string]string{"Content-Range": "bytes 0-2/5"}, true)
	if rec.Code != http.StatusOK {
		t.Fatalf("first chunk: %d %s", rec.Code, rec.Body.String())
	}
	rec = call(server, http.MethodPatch, "/uploads/"+created.UploadID, []byte("lo"),
		map[string]string{"Content-Range": "bytes 3-4/5"}, true)
	if rec.Code != http.StatusOK {
		t.Fatalf("second chunk: %d %s", rec.Code, rec.Body.String())
	}

	rec = call(server, http.MethodPost, "/uploads/"+created.UploadID+"/complete", nil, nil, true)
	if rec.Code != http.StatusOK {
		t.Fatalf("complete: %d %s", rec.Code, rec.Body.String())
	}
	var done Upload
	_ = json.Unmarshal(rec.Body.Bytes(), &done)
	if done.Status != "waiting_scan" {
		t.Fatalf("status after complete = %q", done.Status)
	}
	if scanner.triggers != 1 {
		t.Fatalf("scan triggers = %d", scanner.triggers)
	}

	scanner.trackID = "42"
	rec = call(server, http.MethodGet, "/uploads/"+created.UploadID, nil, nil, true)
	_ = json.Unmarshal(rec.Body.Bytes(), &done)
	if done.Status != "done" || done.ServerTrackID != "42" {
		t.Fatalf("after verify = %+v", done)
	}

	rec = call(server, http.MethodPost, "/library/match", []byte(fmt.Sprintf(`{"sha256":"%s","metadata":%s}`, hash, meta)), nil, true)
	var match MatchResult
	_ = json.Unmarshal(rec.Body.Bytes(), &match)
	if match.Decision != "duplicate" || match.Match != "hash" {
		t.Fatalf("duplicate match = %+v", match)
	}
}

func TestCloseMetadataIsReviewNotUpload(t *testing.T) {
	server, _ := newTestServer(t)
	server.store.AddFile(&FileRecord{
		SHA256: "sha1", Path: "a.flac",
		Metadata: Metadata{Title: "Golden Hour", Artist: "Mira Sol", DurationMs: 214000},
	})

	body := `{"sha256":"other","metadata":{"title":"golden  hour","artist":"MIRA SOL","durationMs":215000}}`
	rec := call(server, http.MethodPost, "/library/match", []byte(body), nil, true)
	var match MatchResult
	_ = json.Unmarshal(rec.Body.Bytes(), &match)
	if match.Decision != "needs_review" {
		t.Fatalf("close metadata = %+v", match)
	}
}

func TestAuthIsRequired(t *testing.T) {
	server, _ := newTestServer(t)
	rec := call(server, http.MethodPost, "/library/match", []byte(`{}`), nil, false)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("unauthorized = %d", rec.Code)
	}
}

func TestChunkAtWrongOffsetIsRefused(t *testing.T) {
	server, _ := newTestServer(t)
	body := `{"filename":"a.flac","size":5,"sha256":"x","metadata":{"title":"A","artist":"B","durationMs":60000}}`
	rec := call(server, http.MethodPost, "/uploads", []byte(body), nil, true)
	var created struct {
		UploadID string `json:"uploadId"`
	}
	_ = json.Unmarshal(rec.Body.Bytes(), &created)

	rec = call(server, http.MethodPatch, "/uploads/"+created.UploadID, []byte("lo"),
		map[string]string{"Content-Range": "bytes 3-4/5"}, true)
	if rec.Code != http.StatusConflict {
		t.Fatalf("wrong offset = %d %s", rec.Code, rec.Body.String())
	}
}
