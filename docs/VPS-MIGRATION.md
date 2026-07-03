# Migrating from Groq to a self-hosted backend

The client is provider-agnostic: it POSTs to
`{api_base_url}/audio/transcriptions` with a Bearer key. Self-hosting is a
config change, not a code change.

## Requirements

A real VPS (not a PaaS like Heroku — see DECISIONS.md D4): ≥4 GB RAM,
2–4 vCPU. `faster-whisper` `small` int8 fits in 4 GB; `large-v3-turbo` int8
wants ~8 GB for comfortable FR/EN accuracy.

## Server (sketch — verify against current speaches docs before deploying)

[Speaches](https://github.com/speaches-ai/speaches) exposes the
OpenAI-compatible API on top of faster-whisper. Outline:

```yaml
# docker-compose.yml on the VPS
services:
  speaches:
    image: ghcr.io/speaches-ai/speaches:latest-cpu
    restart: unless-stopped
    ports:
      - "127.0.0.1:8000:8000"
    volumes:
      - hf-cache:/root/.cache/huggingface
volumes:
  hf-cache:
```

Then in front of it: TLS + auth. Simplest is Caddy with `basic_auth` or an
API-key header check, exposing `https://stt.yourdomain.tld`. Never expose the
bare HTTP port to the internet.

Alternative image: [hwdsl2/docker-whisper](https://github.com/hwdsl2/docker-whisper)
(same OpenAI-compatible API, includes auth options).

## Client switch

`~/.config/whisper-dictate/config.toml`:

```toml
api_base_url = "https://stt.yourdomain.tld/v1"
model = "Systran/faster-whisper-small"   # model id as exposed by the server
```

`~/.config/whisper-dictate/env`: replace the Groq key with the server's key
(the client sends whichever key it finds as a Bearer token).

Test: `whisper-dictate retry` re-sends the last recording — a quick way to
compare backends on identical audio.
