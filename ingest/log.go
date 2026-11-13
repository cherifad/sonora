package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"
)

// Level is how loud a line is. The service logs at LOG_LEVEL and above (default INFO).
type Level int

const (
	LevelDebug Level = iota
	LevelInfo
	LevelWarn
	LevelError
)

func (l Level) String() string {
	switch l {
	case LevelDebug:
		return "DEBUG"
	case LevelWarn:
		return "WARN"
	case LevelError:
		return "ERROR"
	default:
		return "INFO"
	}
}

// ParseLevel reads LOG_LEVEL. Unknown values fall back to info so a typo never silences the
// service.
func ParseLevel(value string) Level {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "debug", "trace", "verbose":
		return LevelDebug
	case "warn", "warning":
		return LevelWarn
	case "error", "fatal":
		return LevelError
	default:
		return LevelInfo
	}
}

// Logger writes the container log. Two shapes, both made for `docker logs`:
//
//	2026-11-13T14:30:12.345Z  INFO   upload accepted   file=tagged.flac  size=12.4 MiB
//	{"time":"2026-11-13T14:30:12.345Z","level":"info","msg":"upload accepted","file":"tagged.flac"}
//
// The human shape is the default; LOG_FORMAT=json gives one object per line for a log
// collector. A nil *Logger is silent, so tests can build a Server without one.
type Logger struct {
	out  io.Writer
	min  Level
	json bool

	mu  sync.Mutex
	now func() time.Time
}

func NewLogger(out io.Writer, min Level, jsonLines bool) *Logger {
	return &Logger{out: out, min: min, json: jsonLines, now: time.Now}
}

// NewLoggerFromEnv builds the logger from LOG_LEVEL (info) and LOG_FORMAT (text|json).
func NewLoggerFromEnv() *Logger {
	return NewLogger(os.Stdout, ParseLevel(os.Getenv("LOG_LEVEL")), strings.EqualFold(os.Getenv("LOG_FORMAT"), "json"))
}

func (l *Logger) Debug(msg string, kv ...any) { l.log(LevelDebug, msg, kv...) }
func (l *Logger) Info(msg string, kv ...any)  { l.log(LevelInfo, msg, kv...) }
func (l *Logger) Warn(msg string, kv ...any)  { l.log(LevelWarn, msg, kv...) }
func (l *Logger) Error(msg string, kv ...any) { l.log(LevelError, msg, kv...) }

// Enabled says whether a level would be written, so an expensive field can be skipped.
func (l *Logger) Enabled(level Level) bool { return l != nil && level >= l.min }

func (l *Logger) log(level Level, msg string, kv ...any) {
	if l == nil || level < l.min || l.out == nil {
		return
	}
	now := l.now().UTC().Format("2006-01-02T15:04:05.000Z")
	fields := pairsToMap(kv)

	l.mu.Lock()
	defer l.mu.Unlock()
	if l.json {
		line := map[string]any{"time": now, "level": strings.ToLower(level.String()), "msg": msg}
		for key, value := range fields {
			line[key] = value
		}
		encoded, err := json.Marshal(line)
		if err != nil {
			fmt.Fprintf(l.out, `{"time":%q,"level":"error","msg":"log encode failed"}%s`, now, "\n")
			return
		}
		_, _ = fmt.Fprintln(l.out, string(encoded))
		return
	}

	var b strings.Builder
	b.WriteString(now)
	b.WriteString("  ")
	b.WriteString(fmt.Sprintf("%-5s", level.String()))
	b.WriteString("  ")
	b.WriteString(msg)
	for _, pair := range orderedPairs(kv) {
		b.WriteString("  ")
		b.WriteString(pair.key)
		b.WriteString("=")
		b.WriteString(formatValue(pair.value))
	}
	b.WriteString("\n")
	_, _ = io.WriteString(l.out, b.String())
}

type fieldPair struct {
	key   string
	value any
}

// pairsToMap keeps the last value of a repeated key, for the JSON shape.
func pairsToMap(kv []any) map[string]any {
	out := make(map[string]any, len(kv)/2)
	for _, pair := range orderedPairs(kv) {
		out[pair.key] = pair.value
	}
	return out
}

// orderedPairs turns key/value arguments into fields; an odd trailing argument becomes
// "arg" so a mistake in a call site never panics or drops the rest of the line.
func orderedPairs(kv []any) []fieldPair {
	out := make([]fieldPair, 0, (len(kv)+1)/2)
	for i := 0; i < len(kv); i += 2 {
		key, ok := kv[i].(string)
		if !ok || key == "" {
			key = "arg"
		}
		var value any = "(missing)"
		if i+1 < len(kv) {
			value = kv[i+1]
		}
		out = append(out, fieldPair{key: key, value: value})
	}
	return out
}

func formatValue(value any) string {
	switch v := value.(type) {
	case nil:
		return "-"
	case string:
		if v == "" {
			return "-"
		}
		if strings.ContainsAny(v, " \t\"") {
			return fmt.Sprintf("%q", v)
		}
		return v
	case time.Duration:
		return formatDuration(v)
	case error:
		return formatValue(v.Error())
	case fmt.Stringer:
		return formatValue(v.String())
	default:
		return formatValue(fmt.Sprint(v))
	}
}

// Mask keeps the first characters of a secret, enough to tell two tokens apart in a log
// without ever writing one down.
func Mask(secret string) string {
	if len(secret) <= 4 {
		if secret == "" {
			return "(unset)"
		}
		return "***"
	}
	return secret[:2] + "…" + secret[len(secret)-2:]
}

// ── HTTP ──

type statusWriter struct {
	http.ResponseWriter
	status int
	bytes  int
}

func (w *statusWriter) WriteHeader(code int) {
	w.status = code
	w.ResponseWriter.WriteHeader(code)
}

func (w *statusWriter) Write(b []byte) (int, error) {
	if w.status == 0 {
		w.status = http.StatusOK
	}
	n, err := w.ResponseWriter.Write(b)
	w.bytes += n
	return n, err
}

// RequestID ties the app's error to a line in the container log: it is echoed as
// X-Request-Id, so `docker logs | grep <id>` finds the request.
const requestIDHeader = "X-Request-Id"

// Middleware logs one line per request: INFO for a normal answer, WARN for a client error
// (4xx) and ERROR for a server error (5xx). A health probe is logged at DEBUG so a monitor
// does not bury the log.
func (l *Logger) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		id := strings.TrimSpace(r.Header.Get(requestIDHeader))
		if id == "" {
			id = newRequestID()
		}
		w.Header().Set(requestIDHeader, id)
		rec := &statusWriter{ResponseWriter: w, status: http.StatusOK}

		next.ServeHTTP(rec, r)

		level := LevelInfo
		switch {
		case rec.status >= 500:
			level = LevelError
		case rec.status >= 400:
			level = LevelWarn
		case r.URL.Path == "/health":
			level = LevelDebug
		}
		// A refused login is a client error but the interesting one: WARN, as above.
		if rec.status == http.StatusUnauthorized || rec.status == http.StatusForbidden {
			level = LevelWarn
		}
		l.log(level, "http", "req", id, "method", r.Method, "path", r.URL.Path,
			"status", rec.status, "bytes", humanBytes(int64(rec.bytes)),
			"took", formatDuration(time.Since(start)), "ip", clientIP(r))
	})
}

func newRequestID() string {
	buf := make([]byte, 4)
	if _, err := rand.Read(buf); err != nil {
		return fmt.Sprintf("%08x", time.Now().UnixNano())
	}
	return hex.EncodeToString(buf)
}

// humanBytes keeps a size to five characters or so, without a space: 512B, 1.0MiB, 4.0GiB.
func humanBytes(n int64) string {
	const unit = 1024
	if n < unit {
		return fmt.Sprintf("%dB", n)
	}
	value := float64(n)
	for _, suffix := range []string{"KiB", "MiB", "GiB", "TiB"} {
		value /= unit
		if value < unit {
			return fmt.Sprintf("%.1f%s", value, suffix)
		}
	}
	return fmt.Sprintf("%.1fPiB", value/unit)
}

// formatDuration shows a request time with the precision that matters: microseconds under a
// millisecond, never the unhelpful "0s" of a rounded fast answer.
func formatDuration(d time.Duration) string {
	switch {
	case d < time.Millisecond:
		return fmt.Sprintf("%dµs", d.Microseconds())
	case d < time.Second:
		return fmt.Sprintf("%dms", d.Round(time.Millisecond).Milliseconds())
	default:
		return fmt.Sprintf("%.2fs", d.Seconds())
	}
}
