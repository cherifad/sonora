package main

import (
	"context"
	"errors"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"
)

type Config struct {
	Port                string
	Token               string
	LibraryPath         string
	IncomingPath        string
	ServerType          string
	ScanURL             string
	SubsonicURL         string
	ScanUser            string
	ScanPassword        string
	ScanAPIKey          string
	MaxUploadBytes      int64
	StatePath           string
	SkipAudioValidation bool
	RequireTags         bool
	RequireCover        bool
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func envBool(key string, fallback bool) bool {
	switch strings.ToLower(strings.TrimSpace(os.Getenv(key))) {
	case "1", "true", "yes", "on":
		return true
	case "0", "false", "no", "off":
		return false
	default:
		return fallback
	}
}

func main() {
	logger := NewLoggerFromEnv()
	cfg := Config{
		Port:                env("PORT", "8088"),
		Token:               os.Getenv("TOKEN"),
		LibraryPath:         env("LIBRARY_PATH", "/music"),
		IncomingPath:        os.Getenv("INCOMING_PATH"),
		ServerType:          env("SERVER_TYPE", "navidrome"),
		ScanURL:             os.Getenv("SCAN_URL"),
		SubsonicURL:         os.Getenv("SUBSONIC_URL"),
		ScanUser:            os.Getenv("SCAN_USER"),
		ScanPassword:        os.Getenv("SCAN_PASSWORD"),
		ScanAPIKey:          os.Getenv("SCAN_API_KEY"),
		MaxUploadBytes:      4 << 30,
		StatePath:           os.Getenv("STATE_PATH"),
		RequireTags:         envBool("REQUIRE_TAGS", true),
		RequireCover:        envBool("REQUIRE_COVER_ART", true),
		SkipAudioValidation: envBool("SKIP_AUDIO_VALIDATION", false),
	}
	if cfg.Token == "" {
		logger.Error("refusing to start", "reason", "TOKEN is required", "hint", "openssl rand -hex 32")
		os.Exit(1)
	}
	if cfg.IncomingPath == "" {
		cfg.IncomingPath = filepath.Join(cfg.LibraryPath, ".sonora-ingest")
	}
	if cfg.StatePath == "" {
		cfg.StatePath = filepath.Join(cfg.IncomingPath, "state.json")
	}
	if raw := os.Getenv("MAX_UPLOAD_BYTES"); raw != "" {
		if value, err := strconv.ParseInt(raw, 10, 64); err == nil {
			cfg.MaxUploadBytes = value
		}
	}
	if err := os.MkdirAll(filepath.Join(cfg.IncomingPath, "tmp"), 0o755); err != nil {
		logger.Error("cannot create the incoming folder", "path", cfg.IncomingPath, "detail", err)
		os.Exit(1)
	}
	store, err := NewStore(cfg.StatePath)
	if err != nil {
		logger.Error("cannot read the upload state", "path", cfg.StatePath, "detail", err)
		os.Exit(1)
	}
	files, uploads := store.Counts()
	logger.Info("state loaded", "path", cfg.StatePath, "files", files, "uploads", uploads)
	authBase := cfg.SubsonicURL
	if authBase == "" {
		authBase = cfg.ScanURL
	}
	var authClient *SubsonicScanner
	if authBase != "" {
		authClient = &SubsonicScanner{base: authBase, http: &http.Client{Timeout: 20 * time.Second}}
	}
	ttl := 30 * 24 * time.Hour
	if raw := os.Getenv("TOKEN_TTL_HOURS"); raw != "" {
		if hours, err := strconv.Atoi(raw); err == nil && hours > 0 {
			ttl = time.Duration(hours) * time.Hour
		}
	}
	allowed := map[string]bool{}
	for _, user := range strings.Split(os.Getenv("ALLOWED_USERS"), ",") {
		if user = strings.TrimSpace(user); user != "" {
			allowed[user] = true
		}
	}
	var mb *MusicBrainz
	if envBool("VERIFY_MUSICBRAINZ", true) {
		mb = NewMusicBrainz(os.Getenv("MUSICBRAINZ_URL"), os.Getenv("MUSICBRAINZ_USER_AGENT"))
		mb.log = logger
	}
	server := &Server{
		cfg: cfg, store: store, scanner: NewScanner(cfg),
		authClient: authClient, issuer: NewTokenIssuer(cfg.Token, ttl),
		limiter: newRateLimiter(5, time.Minute), allowedUsers: allowed, mb: mb,
		log: logger,
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/auth/exchange", server.handleExchange)
	mux.HandleFunc("/health", server.auth(server.handleHealth))
	mux.HandleFunc("/library/match", server.auth(server.handleMatch))
	mux.HandleFunc("/uploads", server.auth(server.handleUploads))
	mux.HandleFunc("/uploads/", server.auth(server.handleUploads))
	mux.HandleFunc("/scan", server.auth(server.handleScan))
	httpServer := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           logger.Middleware(mux),
		ReadHeaderTimeout: 10 * time.Second,
	}
	logger.Info("sonora-ingest starting",
		"version", 1,
		"port", cfg.Port,
		"library", cfg.LibraryPath,
		"incoming", cfg.IncomingPath,
		"server", cfg.ServerType,
		"scanner", scanTarget(cfg),
		"requireTags", cfg.RequireTags,
		"requireCover", cfg.RequireCover,
		"musicbrainz", mb != nil,
		"allowedUsers", userList(allowed),
		"token", Mask(cfg.Token),
	)
	logger.Debug("configuration", "maxUpload", humanBytes(cfg.MaxUploadBytes), "tokenTTL", ttl.String(),
		"skipAudioValidation", cfg.SkipAudioValidation, "authVia", authBase)
	// The tag and cover checks run inside ffprobe: without it the service still works, but it
	// cannot prove what it accepts, and the user should know why the checks are quiet.
	if !cfg.SkipAudioValidation && (cfg.RequireTags || cfg.RequireCover) {
		if path, err := exec.LookPath("ffprobe"); err != nil || path == "" {
			logger.Warn("ffprobe not found", "effect", "tag and cover checks are skipped",
				"hint", "use the Docker image, or install ffmpeg and set SKIP_AUDIO_VALIDATION=0")
		}
	}

	// A container gets SIGTERM on `docker compose down`: finish the requests in flight and say
	// so in the log, instead of dying mid-upload with nothing written.
	stop, cancelStop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancelStop()
	go func() {
		<-stop.Done()
		logger.Info("stopping", "reason", "signal")
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := httpServer.Shutdown(ctx); err != nil {
			logger.Warn("shutdown incomplete", "detail", err)
		}
	}()

	if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		logger.Error("listener stopped", "detail", err)
		os.Exit(1)
	}
	logger.Info("stopped")
}

// scanTarget says where the scan is sent, without printing credentials.
func scanTarget(cfg Config) string {
	if cfg.ScanURL == "" {
		return "none"
	}
	return cfg.ScanURL
}

func userList(allowed map[string]bool) string {
	if len(allowed) == 0 {
		return "any"
	}
	names := make([]string, 0, len(allowed))
	for name := range allowed {
		names = append(names, name)
	}
	return strings.Join(names, ",")
}
