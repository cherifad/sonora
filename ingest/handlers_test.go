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
	"os"
	"path/filepath"
	"testing"
	"time"
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
	mux.HandleFunc("/auth/exchange", server.handleExchange)
	mux.HandleFunc("/library/match", server.auth(server.handleMatch))
	mux.HandleFunc("/uploads", server.auth(server.handleUploads))
	mux.HandleFunc("/uploads/", server.auth(server.handleUploads))
	mux.ServeHTTP(rec, req)
	return rec
}

func callWithToken(server *Server, method, path, token string, body []byte) *httptest.ResponseRecorder {
	req := httptest.NewRequest(method, path, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
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

func TestExchangeIssuesAWorkingToken(t *testing.T) {
	fake := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/rest/ping.view" {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		if r.URL.Query().Get("u") != "me" || r.URL.Query().Get("t") != "token" || r.URL.Query().Get("s") != "salt" {
			t.Errorf("exchange did not forward u/t/s: %s", r.URL.RawQuery)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"subsonic-response":{"status":"ok"}}`))
	}))
	defer fake.Close()

	server, _ := newTestServer(t)
	server.authClient = &SubsonicScanner{base: fake.URL, http: http.DefaultClient}
	server.issuer = NewTokenIssuer("secret", time.Hour)
	server.limiter = newRateLimiter(5, time.Minute)
	server.allowedUsers = map[string]bool{"me": true}

	rec := call(server, http.MethodPost, "/auth/exchange", []byte(`{"u":"me","t":"token","s":"salt"}`), nil, false)
	if rec.Code != http.StatusOK {
		t.Fatalf("exchange: %d %s", rec.Code, rec.Body.String())
	}
	var response struct {
		Token string `json:"token"`
		User  string `json:"user"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	if response.Token == "" || response.User != "me" {
		t.Fatalf("exchange response = %+v", response)
	}

	rec = callWithToken(server, http.MethodPost, "/library/match",
		response.Token, []byte(`{"sha256":"sha","metadata":{"title":"T","artist":"A"}}`))
	if rec.Code != http.StatusOK {
		t.Fatalf("signed token refused: %d %s", rec.Code, rec.Body.String())
	}
}

func TestExchangeRefusesBadCredentials(t *testing.T) {
	fake := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"subsonic-response":{"status":"failed","error":{"code":40,"message":"bad"}}}`))
	}))
	defer fake.Close()

	server, _ := newTestServer(t)
	server.authClient = &SubsonicScanner{base: fake.URL, http: http.DefaultClient}
	server.issuer = NewTokenIssuer("secret", time.Hour)
	server.limiter = newRateLimiter(5, time.Minute)

	rec := call(server, http.MethodPost, "/auth/exchange", []byte(`{"u":"me","t":"token","s":"salt"}`), nil, false)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("bad exchange = %d %s", rec.Code, rec.Body.String())
	}
}

func TestAServerMatchIsNeedsReview(t *testing.T) {
	server, scanner := newTestServer(t)
	scanner.trackID = "42"
	body := `{"sha256":"new-hash","metadata":{"title":"T","artist":"A","durationMs":60000}}`
	rec := call(server, http.MethodPost, "/library/match", []byte(body), nil, true)
	var match MatchResult
	_ = json.Unmarshal(rec.Body.Bytes(), &match)
	if match.Decision != "needs_review" {
		t.Fatalf("server match = %+v", match)
	}
}

func TestCreateUploadRefusesAServerMatch(t *testing.T) {
	server, scanner := newTestServer(t)
	scanner.trackID = "42"
	body := `{"filename":"a.flac","size":5,"sha256":"new-hash","metadata":{"title":"T","artist":"A","durationMs":60000}}`
	rec := call(server, http.MethodPost, "/uploads", []byte(body), nil, true)
	if rec.Code != http.StatusOK {
		t.Fatalf("create = %d %s", rec.Code, rec.Body.String())
	}
	var response struct {
		Decision string `json:"decision"`
	}
	_ = json.Unmarshal(rec.Body.Bytes(), &response)
	if response.Decision != "needs_review" {
		t.Fatalf("create response = %s", rec.Body.String())
	}
}

func TestALocalHashDuplicateBeatsTheServerCheck(t *testing.T) {
	server, scanner := newTestServer(t)
	scanner.trackID = "42"
	server.store.AddFile(&FileRecord{
		SHA256: "known-hash", ServerTrackID: "7",
		Metadata: Metadata{Title: "T", Artist: "A", DurationMs: 60000},
	})
	body := `{"sha256":"known-hash","metadata":{"title":"T","artist":"A","durationMs":60000}}`
	rec := call(server, http.MethodPost, "/library/match", []byte(body), nil, true)
	var match MatchResult
	_ = json.Unmarshal(rec.Body.Bytes(), &match)
	if match.Decision != "duplicate" || match.ServerTrackID != "7" {
		t.Fatalf("local duplicate = %+v", match)
	}
}

func TestDeletingAnUploadRemovesTheFileAndTheRecord(t *testing.T) {
	server, _ := newTestServer(t)
	meta := `{"title":"Delete Me","artist":"Tester","album":"Delete Me","track":1,"durationMs":60000}`
	hash := sha256Hex("delete-me")
	create := fmt.Sprintf(`{"filename":"Delete Me.flac","size":9,"sha256":"%s","metadata":%s}`, hash, meta)
	rec := call(server, http.MethodPost, "/uploads", []byte(create), nil, true)
	if rec.Code != http.StatusCreated {
		t.Fatalf("create: %d %s", rec.Code, rec.Body.String())
	}
	var created struct {
		UploadID string `json:"uploadId"`
	}
	_ = json.Unmarshal(rec.Body.Bytes(), &created)
	rec = call(server, http.MethodPatch, "/uploads/"+created.UploadID, []byte("delete-me"),
		map[string]string{"Content-Range": "bytes 0-8/9"}, true)
	if rec.Code != http.StatusOK {
		t.Fatalf("chunk: %d %s", rec.Code, rec.Body.String())
	}
	rec = call(server, http.MethodPost, "/uploads/"+created.UploadID+"/complete", nil, nil, true)
	if rec.Code != http.StatusOK {
		t.Fatalf("complete: %d %s", rec.Code, rec.Body.String())
	}
	var done Upload
	_ = json.Unmarshal(rec.Body.Bytes(), &done)
	if done.Path == "" {
		t.Fatal("no path after complete")
	}
	full := filepath.Join(server.cfg.LibraryPath, done.Path)
	if _, err := os.Stat(full); err != nil {
		t.Fatalf("file missing after complete: %v", err)
	}

	rec = call(server, http.MethodDelete, "/uploads/"+created.UploadID, nil, nil, true)
	if rec.Code != http.StatusNoContent {
		t.Fatalf("delete: %d %s", rec.Code, rec.Body.String())
	}
	if _, err := os.Stat(full); !os.IsNotExist(err) {
		t.Fatalf("file still there after delete: %v", err)
	}
	rec = call(server, http.MethodPost, "/library/match",
		[]byte(fmt.Sprintf(`{"sha256":"%s","metadata":%s}`, hash, meta)), nil, true)
	var match MatchResult
	_ = json.Unmarshal(rec.Body.Bytes(), &match)
	if match.Decision != "new" {
		t.Fatalf("deleted file still matches: %+v", match)
	}
}

func TestCreateUploadNeedsReviewWhenMusicBrainzDoesNotKnowIt(t *testing.T) {
	mb := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"recordings":[]}`))
	}))
	defer mb.Close()

	server, _ := newTestServer(t)
	server.mb = NewMusicBrainz(mb.URL, "sonora-test/1.0")
	body := `{"filename":"a.flac","size":5,"sha256":"new-hash","metadata":{"title":"Made Up","artist":"Nobody","album":"Nowhere","durationMs":60000}}`
	rec := call(server, http.MethodPost, "/uploads", []byte(body), nil, true)
	if rec.Code != http.StatusOK {
		t.Fatalf("create = %d %s", rec.Code, rec.Body.String())
	}
	var response struct {
		Decision string `json:"decision"`
	}
	_ = json.Unmarshal(rec.Body.Bytes(), &response)
	if response.Decision != "needs_review" {
		t.Fatalf("create response = %s", rec.Body.String())
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
