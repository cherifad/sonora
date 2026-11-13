package main

import (
	"context"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

type Server struct {
	cfg          Config
	store        *Store
	scanner      Scanner
	authClient   *SubsonicScanner
	issuer       *TokenIssuer
	limiter      *rateLimiter
	allowedUsers map[string]bool
	mb           *MusicBrainz
}

func writeJSON(w http.ResponseWriter, code int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(value)
}

func (s *Server) auth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		token := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
		staticOK := s.cfg.Token != "" && subtle.ConstantTimeCompare([]byte(token), []byte(s.cfg.Token)) == 1
		signedOK := false
		if !staticOK && token != "" && s.issuer != nil {
			_, err := s.issuer.Verify(token)
			signedOK = err == nil
		}
		if !staticOK && !signedOK {
			writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "unauthorized"})
			return
		}
		next(w, r)
	}
}

type exchangeRequest struct {
	U      string `json:"u"`
	T      string `json:"t"`
	S      string `json:"s"`
	APIKey string `json:"apiKey"`
}

// handleExchange trades the app's existing Subsonic credentials for an upload token.
// The password never reaches the service: the app sends the same u/t/s it would send to
// the server, and the service forwards them to ping.
func (s *Server) handleExchange(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "POST only"})
		return
	}
	if s.limiter != nil && !s.limiter.allow(clientIP(r)) {
		writeJSON(w, http.StatusTooManyRequests, map[string]string{"error": "too many attempts"})
		return
	}
	var req exchangeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
		return
	}
	auth := url.Values{}
	user := req.U
	if req.APIKey != "" {
		auth.Set("apiKey", req.APIKey)
		if user == "" {
			user = "api-key"
		}
	} else {
		if req.U == "" || req.T == "" || req.S == "" {
			writeJSON(w, http.StatusUnprocessableEntity, map[string]string{"error": "u, t and s are required"})
			return
		}
		auth.Set("u", req.U)
		auth.Set("t", req.T)
		auth.Set("s", req.S)
	}
	if s.authClient == nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "no Subsonic endpoint configured"})
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	if err := s.authClient.Ping(ctx, auth); err != nil {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "the server refused these credentials"})
		return
	}
	if len(s.allowedUsers) > 0 && !s.allowedUsers[user] {
		writeJSON(w, http.StatusForbidden, map[string]string{"error": "this user is not allowed to upload"})
		return
	}
	token, expires, err := s.issuer.Issue(user, serverID(s.cfg))
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"token": token, "expiresAt": expires, "user": user, "server": s.cfg.ServerType,
	})
}

func clientIP(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

func (s *Server) handleHealth(w http.ResponseWriter, _ *http.Request) {
	ffprobe := false
	if path, err := exec.LookPath("ffprobe"); err == nil && path != "" {
		ffprobe = true
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"ok":          true,
		"version":     1,
		"libraryPath": s.cfg.LibraryPath,
		"ffprobe":     ffprobe,
		"serverType":  s.cfg.ServerType,
	})
}

type matchRequest struct {
	SHA256   string   `json:"sha256"`
	Metadata Metadata `json:"metadata"`
}

func (s *Server) handleMatch(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "POST only"})
		return
	}
	var req matchRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
		return
	}
	match := s.store.Match(strings.ToLower(req.SHA256), req.Metadata)
	if match.Decision == "new" && s.serverHasCloseMatch(r.Context(), req.Metadata) {
		match = MatchResult{
			Decision: "needs_review",
			Match:    "server",
			Reason:   "a close match already exists on the server; not uploaded automatically",
		}
	}
	writeJSON(w, http.StatusOK, match)
}

// serverHasCloseMatch asks the music server's own index. The ingest state only knows the
// files it uploaded itself: an album that was already in the library must not be uploaded
// a second time just because the service has never seen it.
func (s *Server) serverHasCloseMatch(ctx context.Context, meta Metadata) bool {
	if s.scanner == nil || meta.Title == "" {
		return false
	}
	ctx, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()
	_, err := s.scanner.Verify(ctx, meta)
	return err == nil
}

type createRequest struct {
	Filename string   `json:"filename"`
	Size     int64    `json:"size"`
	SHA256   string   `json:"sha256"`
	Metadata Metadata `json:"metadata"`
}

func (s *Server) createUpload(w http.ResponseWriter, r *http.Request) {
	var req createRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid JSON"})
		return
	}
	if extensionOf(req.Filename) == "" {
		writeJSON(w, http.StatusUnprocessableEntity, map[string]string{"error": "unsupported audio extension"})
		return
	}
	if req.Size <= 0 || (s.cfg.MaxUploadBytes > 0 && req.Size > s.cfg.MaxUploadBytes) {
		writeJSON(w, http.StatusUnprocessableEntity, map[string]string{"error": "invalid size"})
		return
	}
	if req.Metadata.Title == "" || (req.Metadata.Artist == "" && req.Metadata.AlbumArtist == "") {
		writeJSON(w, http.StatusUnprocessableEntity, map[string]string{"error": "title and artist are required"})
		return
	}
	if local := s.store.Match(strings.ToLower(req.SHA256), req.Metadata); local.Decision == "new" &&
		s.serverHasCloseMatch(r.Context(), req.Metadata) {
		writeJSON(w, http.StatusOK, map[string]any{
			"decision": "needs_review",
			"reason":   "a close match already exists on the server; not uploaded automatically",
		})
		return
	}
	if s.mb != nil {
		ctx, cancel := context.WithTimeout(r.Context(), 20*time.Second)
		err := s.mb.Verify(ctx, req.Metadata)
		cancel()
		if err != nil {
			writeJSON(w, http.StatusOK, map[string]any{"decision": "needs_review", "reason": err.Error()})
			return
		}
	}
	upload, match, err := s.store.CreateUpload(req.Filename, req.Size, strings.ToLower(req.SHA256), req.Metadata)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	switch match.Decision {
	case "duplicate":
		writeJSON(w, http.StatusOK, map[string]any{
			"duplicate": true, "match": match.Match,
			"serverTrackId": match.ServerTrackID, "path": match.Path,
		})
		return
	case "needs_review":
		writeJSON(w, http.StatusOK, map[string]any{"decision": "needs_review", "reason": match.Reason})
		return
	}
	tmp := s.tempPath(upload.ID)
	if err := os.MkdirAll(filepath.Dir(tmp), 0o755); err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	file, err := os.OpenFile(tmp, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o644)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	_ = file.Close()
	writeJSON(w, http.StatusCreated, map[string]any{
		"uploadId": upload.ID, "offset": 0, "chunkSize": 8 << 20,
	})
}

func (s *Server) patchUpload(w http.ResponseWriter, r *http.Request, id string) {
	upload, ok := s.store.Upload(id)
	if !ok {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "unknown upload"})
		return
	}
	if upload.Status != "uploading" {
		writeJSON(w, http.StatusConflict, map[string]string{"error": "upload is " + upload.Status})
		return
	}
	start, end, total, err := parseContentRange(r.Header.Get("Content-Range"))
	if err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": err.Error()})
		return
	}
	if total > 0 && total != upload.Size {
		writeJSON(w, http.StatusConflict, map[string]string{"error": "size does not match the upload"})
		return
	}
	if start != upload.Offset {
		writeJSON(w, http.StatusConflict, map[string]any{"error": "wrong offset", "offset": upload.Offset})
		return
	}
	file, err := os.OpenFile(s.tempPath(id), os.O_WRONLY|os.O_APPEND, 0o644)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	defer file.Close()
	written, err := io.CopyN(file, r.Body, end-start+1)
	if err != nil || written != end-start+1 {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "short chunk"})
		return
	}
	s.store.SetOffset(id, end+1)
	writeJSON(w, http.StatusOK, map[string]any{"offset": end + 1})
}

func (s *Server) completeUpload(w http.ResponseWriter, r *http.Request, id string) {
	upload, ok := s.store.Upload(id)
	if !ok {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "unknown upload"})
		return
	}
	if upload.Offset != upload.Size {
		writeJSON(w, http.StatusConflict, map[string]any{"error": "upload incomplete", "offset": upload.Offset})
		return
	}
	tmp := s.tempPath(id)
	hash, err := sha256File(tmp)
	if err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	if upload.SHA256 != "" && hash != upload.SHA256 {
		_ = os.Remove(tmp)
		s.store.SetStatus(id, "failed", "sha256 mismatch")
		writeJSON(w, http.StatusUnprocessableEntity, map[string]string{"error": "sha256 mismatch"})
		return
	}
	if !s.cfg.SkipAudioValidation {
		if _, err := validateAudio(tmp, upload.Metadata, s.cfg.RequireTags, s.cfg.RequireCover); err != nil {
			_ = os.Remove(tmp)
			s.store.SetStatus(id, "failed", err.Error())
			writeJSON(w, http.StatusUnprocessableEntity, map[string]string{"error": err.Error()})
			return
		}
	}
	target := uniquePath(s.cfg.LibraryPath, targetPath(upload.Metadata, extensionOf(upload.Filename)))
	if !underRoot(s.cfg.LibraryPath, target) {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid target path"})
		return
	}
	if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	if err := os.Rename(tmp, target); err != nil {
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
		return
	}
	rel, _ := filepath.Rel(s.cfg.LibraryPath, target)
	now := time.Now().UTC()
	s.store.AddFile(&FileRecord{
		SHA256: hash, Path: rel, ISRC: upload.Metadata.ISRC, MBID: upload.Metadata.MBID,
		Metadata: upload.Metadata, CreatedAt: now,
	})
	s.store.SetPath(id, rel)
	if s.scanner == nil {
		s.store.SetStatus(id, "waiting_scan", "no scanner configured: set SCAN_URL and SCAN_USER/SCAN_PASSWORD")
	} else {
		ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
		defer cancel()
		if err := s.scanner.Trigger(ctx); err != nil {
			s.store.SetStatus(id, "waiting_scan", "scan trigger failed: "+err.Error())
		} else {
			s.store.SetStatus(id, "waiting_scan", "")
		}
	}
	updated, _ := s.store.Upload(id)
	writeJSON(w, http.StatusOK, updated)
}

func (s *Server) statusUpload(w http.ResponseWriter, r *http.Request, id string) {
	upload, ok := s.store.Upload(id)
	if !ok {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "unknown upload"})
		return
	}
	if upload.Status == "waiting_scan" {
		if s.scanner == nil {
			s.store.SetStatus(id, "waiting_scan", "no scanner configured: set SCAN_URL and SCAN_USER/SCAN_PASSWORD")
			upload, _ = s.store.Upload(id)
		} else {
			ctx, cancel := context.WithTimeout(r.Context(), 8*time.Second)
			defer cancel()
			if trackID, err := s.scanner.Verify(ctx, upload.Metadata); err == nil && trackID != "" {
				s.store.SetServerTrackID(id, trackID)
				s.store.SetFileServerTrackID(upload.SHA256, trackID)
				upload, _ = s.store.Upload(id)
			} else if err != nil {
				// Keep waiting, but tell the app why the scan has not produced the track yet.
				s.store.SetStatus(id, "waiting_scan", err.Error())
				upload, _ = s.store.Upload(id)
			}
		}
	}
	writeJSON(w, http.StatusOK, upload)
}

func (s *Server) deleteUpload(w http.ResponseWriter, _ *http.Request, id string) {
	upload, ok := s.store.Upload(id)
	if !ok {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "unknown upload"})
		return
	}
	if upload.Status == "uploading" {
		_ = os.Remove(s.tempPath(id))
	}
	if upload.Path != "" {
		full := filepath.Join(s.cfg.LibraryPath, upload.Path)
		if underRoot(s.cfg.LibraryPath, full) {
			_ = os.Remove(full)
		}
	}
	if upload.SHA256 != "" {
		s.store.RemoveFile(upload.SHA256)
	}
	s.store.RemoveUpload(id)
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleUploads(w http.ResponseWriter, r *http.Request) {
	path := strings.Trim(strings.TrimPrefix(r.URL.Path, "/uploads"), "/")
	if path == "" {
		if r.Method == http.MethodPost {
			s.createUpload(w, r)
			return
		}
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "POST only"})
		return
	}
	if strings.HasSuffix(path, "/complete") {
		if r.Method == http.MethodPost {
			s.completeUpload(w, r, strings.TrimSuffix(path, "/complete"))
			return
		}
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "POST only"})
		return
	}
	switch r.Method {
	case http.MethodPatch:
		s.patchUpload(w, r, path)
	case http.MethodGet:
		s.statusUpload(w, r, path)
	case http.MethodDelete:
		s.deleteUpload(w, r, path)
	default:
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "unsupported method"})
	}
}

func (s *Server) handleScan(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, http.StatusMethodNotAllowed, map[string]string{"error": "POST only"})
		return
	}
	if s.scanner == nil {
		writeJSON(w, http.StatusServiceUnavailable, map[string]string{"error": "no scanner configured"})
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()
	if err := s.scanner.Trigger(ctx); err != nil {
		writeJSON(w, http.StatusBadGateway, map[string]string{"error": err.Error()})
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) tempPath(id string) string {
	return filepath.Join(s.cfg.IncomingPath, "tmp", id+".part")
}

func parseContentRange(value string) (start, end, total int64, err error) {
	value = strings.TrimSpace(strings.TrimPrefix(value, "bytes "))
	parts := strings.SplitN(value, "/", 2)
	if len(parts) != 2 {
		return 0, 0, 0, errors.New("Content-Range must be bytes start-end/total")
	}
	rangePart := parts[0]
	if parts[1] != "*" {
		total, err = strconv.ParseInt(parts[1], 10, 64)
		if err != nil {
			return 0, 0, 0, errors.New("invalid total in Content-Range")
		}
	}
	bounds := strings.SplitN(rangePart, "-", 2)
	if len(bounds) != 2 {
		return 0, 0, 0, errors.New("invalid range in Content-Range")
	}
	start, err = strconv.ParseInt(bounds[0], 10, 64)
	if err != nil {
		return 0, 0, 0, errors.New("invalid start in Content-Range")
	}
	end, err = strconv.ParseInt(bounds[1], 10, 64)
	if err != nil || end < start {
		return 0, 0, 0, errors.New("invalid end in Content-Range")
	}
	return start, end, total, nil
}

func sha256File(path string) (string, error) {
	file, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer file.Close()
	hash := sha256.New()
	if _, err := io.Copy(hash, file); err != nil {
		return "", err
	}
	return hex.EncodeToString(hash.Sum(nil)), nil
}

// validateAudio uses ffprobe when present (audio stream, duration, embedded tags and cover)
// and falls back to the duration the app sent. A file shorter than 30 s is not music for this
// service; with requireTags/requireCover the file must also carry the tags it claims and a
// picture stream.
func validateAudio(path string, meta Metadata, requireTags, requireCover bool) (int64, error) {
	if ffprobe, err := exec.LookPath("ffprobe"); err == nil && ffprobe != "" {
		cmd := exec.Command(ffprobe, "-v", "error",
			"-show_entries", "format=duration:format_tags=title,artist,album,album_artist:stream=codec_type:stream_disposition=attached_pic",
			"-of", "json", path)
		out, err := cmd.Output()
		if err != nil {
			return 0, fmt.Errorf("ffprobe: %w", err)
		}
		var probe struct {
			Format struct {
				Duration string            `json:"duration"`
				Tags     map[string]string `json:"tags"`
			} `json:"format"`
			Streams []struct {
				CodecType   string `json:"codec_type"`
				Disposition struct {
					AttachedPic int `json:"attached_pic"`
				} `json:"disposition"`
			} `json:"streams"`
		}
		if err := json.Unmarshal(out, &probe); err != nil {
			return 0, fmt.Errorf("ffprobe output: %w", err)
		}
		audio, cover := false, false
		for _, stream := range probe.Streams {
			if stream.CodecType == "audio" {
				audio = true
			}
			if stream.CodecType == "video" && stream.Disposition.AttachedPic == 1 {
				cover = true
			}
		}
		if !audio {
			return 0, errors.New("not an audio file")
		}
		if requireCover && !cover {
			return 0, errors.New("no embedded cover art")
		}
		if requireTags {
			title := tagValue(probe.Format.Tags, "title")
			artist := tagValue(probe.Format.Tags, "artist")
			album := tagValue(probe.Format.Tags, "album")
			if normalize(title) != meta.normalizedTitle() ||
				!artistLike(artist, meta.Artist) ||
				(meta.Album != "" && normalize(album) != normalize(meta.Album)) {
				return 0, errors.New("the file tags do not match the uploaded metadata")
			}
		}
		seconds, _ := strconv.ParseFloat(probe.Format.Duration, 64)
		ms := int64(seconds * 1000)
		if ms > 0 && ms < 30_000 {
			return 0, fmt.Errorf("too short (%d ms)", ms)
		}
		return ms, nil
	}
	if meta.DurationMs > 0 && meta.DurationMs < 30_000 {
		return 0, fmt.Errorf("too short (%d ms)", meta.DurationMs)
	}
	return meta.DurationMs, nil
}

// tagValue reads an ffprobe tag whatever case the container uses for its keys.
func tagValue(tags map[string]string, key string) string {
	for k, v := range tags {
		if strings.EqualFold(k, key) {
			return v
		}
	}
	return ""
}

func uniquePath(root, rel string) string {
	full := filepath.Join(root, rel)
	if _, err := os.Stat(full); errors.Is(err, os.ErrNotExist) {
		return full
	}
	ext := filepath.Ext(full)
	base := strings.TrimSuffix(full, ext)
	for i := 2; i < 100; i++ {
		candidate := fmt.Sprintf("%s (%d)%s", base, i, ext)
		if _, err := os.Stat(candidate); errors.Is(err, os.ErrNotExist) {
			return candidate
		}
	}
	return filepath.Join(root, "Incoming", fmt.Sprintf("%d%s", time.Now().UnixNano(), ext))
}

func underRoot(root, full string) bool {
	rel, err := filepath.Rel(root, full)
	return err == nil && rel != ".." && !strings.HasPrefix(rel, ".."+string(filepath.Separator))
}
