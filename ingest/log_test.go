package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestLevelsBelowTheMinimumAreDropped(t *testing.T) {
	var buf bytes.Buffer
	log := NewLogger(&buf, LevelInfo, false)

	log.Debug("not written")
	log.Info("kept", "file", "a.flac")
	log.Warn("also kept")

	out := buf.String()
	if strings.Contains(out, "not written") {
		t.Fatalf("debug leaked at info level: %q", out)
	}
	for _, want := range []string{"INFO", "kept", "file=a.flac", "WARN"} {
		if !strings.Contains(out, want) {
			t.Fatalf("missing %q in %q", want, out)
		}
	}
}

func TestHumanLineQuotesValuesWithSpaces(t *testing.T) {
	var buf bytes.Buffer
	log := NewLogger(&buf, LevelDebug, false)
	log.Info("upload refused", "reason", "title and artist are required", "size", 12)

	out := buf.String()
	if !strings.Contains(out, `reason="title and artist are required"`) {
		t.Fatalf("a value with spaces must be quoted: %q", out)
	}
	if !strings.Contains(out, "size=12") {
		t.Fatalf("numbers stay bare: %q", out)
	}
	if strings.Count(out, "\n") != 1 {
		t.Fatalf("one line per call: %q", out)
	}
}

func TestJSONFormatIsOneObjectPerLine(t *testing.T) {
	var buf bytes.Buffer
	log := NewLogger(&buf, LevelDebug, true)
	log.Warn("scan trigger failed", "detail", "HTTP 502", "attempt", 2)

	var line map[string]any
	if err := json.Unmarshal(bytes.TrimSpace(buf.Bytes()), &line); err != nil {
		t.Fatalf("not JSON: %v (%q)", err, buf.String())
	}
	if line["level"] != "warn" || line["msg"] != "scan trigger failed" || line["detail"] != "HTTP 502" {
		t.Fatalf("unexpected fields: %#v", line)
	}
	if _, err := time.Parse(time.RFC3339Nano, line["time"].(string)); err != nil {
		t.Fatalf("time is not RFC3339: %#v", line["time"])
	}
}

func TestOddArgumentsAndNilLoggerDoNotPanic(t *testing.T) {
	var buf bytes.Buffer
	NewLogger(&buf, LevelInfo, false).Info("half a pair", "only-key")
	if !strings.Contains(buf.String(), "only-key=(missing)") {
		t.Fatalf("odd arguments must not drop the line: %q", buf.String())
	}

	var silent *Logger
	silent.Info("no logger here")
	silent.Warn("still fine", "key", "value")
	if silent.Enabled(LevelError) {
		t.Fatal("a nil logger enables nothing")
	}
}

func TestMaskNeverWritesTheSecret(t *testing.T) {
	if got := Mask("abcdefgh"); got != "ab…gh" {
		t.Fatalf("mask = %q", got)
	}
	if got := Mask("abc"); got != "***" {
		t.Fatalf("short secret = %q", got)
	}
	if got := Mask(""); got != "(unset)" {
		t.Fatalf("empty secret = %q", got)
	}
	full := Mask("0123456789abcdef")
	if strings.Contains(full, "23456789abcd") {
		t.Fatalf("mask leaks the middle: %q", full)
	}
}

func TestMiddlewareLogsTheRequestAndEchoesAnID(t *testing.T) {
	var buf bytes.Buffer
	log := NewLogger(&buf, LevelInfo, false)
	handler := log.Middleware(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnprocessableEntity)
		_, _ = w.Write([]byte(`{"error":"x"}`))
	}))

	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, httptest.NewRequest(http.MethodPost, "/uploads", nil))

	if rec.Header().Get(requestIDHeader) == "" {
		t.Fatal("no request id echoed")
	}
	out := buf.String()
	if !strings.Contains(out, "WARN") || !strings.Contains(out, "status=422") || !strings.Contains(out, "path=/uploads") {
		t.Fatalf("a 4xx should be a WARN line with the status: %q", out)
	}
}

func TestHealthIsQuietUnlessDebug(t *testing.T) {
	var buf bytes.Buffer
	handler := NewLogger(&buf, LevelInfo, false).Middleware(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	handler.ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodGet, "/health", nil))
	if buf.Len() != 0 {
		t.Fatalf("a health probe should not fill the log: %q", buf.String())
	}

	buf.Reset()
	handler = NewLogger(&buf, LevelDebug, false).Middleware(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	handler.ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodGet, "/health", nil))
	if !strings.Contains(buf.String(), "path=/health") {
		t.Fatalf("debug should show the probe: %q", buf.String())
	}
}
