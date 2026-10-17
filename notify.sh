#!/usr/bin/env bash
#
# notify.sh — ntfy notifications for the nightly update run.
#
# Sourced by cron.sh; run directly with --test to send a probe notification and check the setup.
#
# The interface is deliberately the one the pihole-update job on this host already uses — same
# function signature, same variable names, same meanings — so neither script has to be re-read to
# understand the other:
#
#     notify <title> <message> [priority] [tags]
#
# Secrets and local overrides, not in Git:
#
#     ENV_FILE="${ENV_FILE:-$HOME/.config/w2s-update.env}"
#
#     NTFY_URL=https://ntfy.example.net/my-topic
#     NTFY_TOKEN=tk_...
#
# NTFY_URL is the full topic URL and, as in pihole-update.sh, empty means "do not notify" rather
# than "fall back to something" — a host that was never configured stays quiet instead of guessing
# where to publish. Which server and which topic is deployment configuration and stays in that
# file: the address of a piece of home infrastructure is not something a public repository should
# carry, and a topic URL is itself close to a credential on a server that allows anonymous
# publishing. NTFY_TOKEN is the token of a user with write access to the topic; `chmod 600` the
# file, nothing in this repository is a place to keep one.
#
# Nothing in here may fail the update: a notifier that is down, misconfigured or slow must not keep
# a deployment from happening, so every path returns 0 and curl is bounded by a timeout.

# Secrets & local overrides (NTFY_URL, NTFY_TOKEN, ...) – not in Git
ENV_FILE="${ENV_FILE:-$HOME/.config/w2s-update.env}"
# shellcheck disable=SC1090
[[ -r "$ENV_FILE" ]] && source "$ENV_FILE"

NTFY_URL="${NTFY_URL:-}"      # optional, full topic URL, e.g. https://ntfy.sh/my-topic
NTFY_TOKEN="${NTFY_TOKEN:-}"  # token of a user with write access to the topic

notify() {
    local title="$1" msg="$2" prio="${3:-default}" tags="${4:-}"
    [[ -n "$NTFY_URL" ]] || return 0
    local -a auth=()
    [[ -n "$NTFY_TOKEN" ]] && auth=(-H "Authorization: Bearer $NTFY_TOKEN")
    # Body on stdin rather than -d "$msg": curl reads a *file* when the argument starts with '@',
    # and the failure notification carries a tail of the run log, which can begin with anything.
    printf '%s' "$msg" | curl -fsS --max-time 10 "${auth[@]}" \
        -H "Title: $title" -H "Priority: $prio" -H "Tags: $tags" \
        --data-binary @- "$NTFY_URL" >/dev/null 2>&1 || echo "ntfy notification failed" >&2
    return 0
}

# ./notify.sh --test  → check that the configuration actually reaches the topic, change nothing
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    [[ "${1:-}" == "--test" ]] || { echo "Usage: source notify.sh   |   $0 --test" >&2; exit 64; }
    [[ -n "$NTFY_URL" ]] || { echo "NTFY_URL is not set (ENV_FILE: $ENV_FILE)" >&2; exit 1; }
    notify "w2s update: test" "Notification works. HEAD: $(git rev-parse --short HEAD 2>/dev/null || echo unknown)" low test_tube
fi
