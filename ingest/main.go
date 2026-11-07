package main

import (
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
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
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func main() {
	cfg := Config{
		Port:           env("PORT", "8088"),
		Token:          os.Getenv("TOKEN"),
		LibraryPath:    env("LIBRARY_PATH", "/music"),
		IncomingPath:   os.Getenv("INCOMING_PATH"),
		ServerType:     env("SERVER_TYPE", "navidrome"),
		ScanURL:        os.Getenv("SCAN_URL"),
		SubsonicURL:    os.Getenv("SUBSONIC_URL"),
		ScanUser:       os.Getenv("SCAN_USER"),
		ScanPassword:   os.Getenv("SCAN_PASSWORD"),
		ScanAPIKey:     os.Getenv("SCAN_API_KEY"),
		MaxUploadBytes: 4 << 30,
		StatePath:      os.Getenv("STATE_PATH"),
	}
	if cfg.Token == "" {
		log.Fatal("TOKEN is required (generate one with: openssl rand -hex 32)")
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
		log.Fatal(err)
	}
	store, err := NewStore(cfg.StatePath)
	if err != nil {
		log.Fatal(err)
	}
	server := &Server{cfg: cfg, store: store, scanner: NewScanner(cfg)}
	mux := http.NewServeMux()
	mux.HandleFunc("/health", server.auth(server.handleHealth))
	mux.HandleFunc("/library/match", server.auth(server.handleMatch))
	mux.HandleFunc("/uploads", server.auth(server.handleUploads))
	mux.HandleFunc("/uploads/", server.auth(server.handleUploads))
	mux.HandleFunc("/scan", server.auth(server.handleScan))
	httpServer := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           mux,
		ReadHeaderTimeout: 10 * time.Second,
	}
	log.Printf("sonora-ingest %s on :%s, library %s", "1", cfg.Port, cfg.LibraryPath)
	log.Fatal(httpServer.ListenAndServe())
}
