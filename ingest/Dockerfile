# Build
FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /sonora-ingest .

# Runtime: ffprobe validates the audio and its duration.
FROM alpine:3.20
RUN apk add --no-cache ca-certificates ffmpeg
COPY --from=build /sonora-ingest /usr/local/bin/sonora-ingest
ENV PORT=8088 \
    LIBRARY_PATH=/music \
    INCOMING_PATH=/music/.sonora-ingest
EXPOSE 8088
USER 1000:1000
ENTRYPOINT ["/usr/local/bin/sonora-ingest"]
